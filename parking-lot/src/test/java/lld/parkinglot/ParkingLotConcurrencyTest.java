package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Timeout;

/** Several gates (threads) hitting the lot at the same instant. */
@Timeout(10)
class ParkingLotConcurrencyTest {

    private static final int GATES = 8;

    /** Submits all tasks, releases them at once with a latch, and collects results. */
    private static <T> List<T> runTogether(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(GATES)) {
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    @RepeatedTest(5)
    void manyCarsRacingNeverShareASpotOrOverfill() throws Exception {
        ParkingLot lot = new ParkingLot(
                List.of(new Level(1, 0, 10, 0), new Level(2, 0, 10, 0)), new FirstAvailableStrategy());

        List<Callable<Optional<Ticket>>> arrivals = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Vehicle car = new Car("RACE-" + i);
            arrivals.add(() -> lot.park(car));
        }
        List<Ticket> tickets = runTogether(arrivals).stream().flatMap(Optional::stream).toList();

        Set<String> spots = tickets.stream().map(t -> t.spot().getId()).collect(Collectors.toSet());
        assertEquals(20, tickets.size(), "every spot is filled, no more");
        assertEquals(20, spots.size(), "no two tickets share a spot");
        assertEquals(0, lot.getFreeCount(SpotType.MEDIUM));

        List<Callable<Void>> departures = new ArrayList<>();
        for (Ticket t : tickets) {
            departures.add(() -> {
                lot.unpark(t.id());
                return null;
            });
        }
        runTogether(departures);
        assertEquals(20, lot.getFreeCount(SpotType.MEDIUM), "counters did not drift");
    }

    @RepeatedTest(5)
    void samePlateFromManyGatesParksExactlyOnce() throws Exception {
        ParkingLot lot = new ParkingLot(List.of(new Level(1, 0, 10, 0)), new FirstAvailableStrategy());

        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int i = 0; i < GATES; i++) {
            attempts.add(() -> {
                try {
                    return lot.park(new Car("SAME")).isPresent();
                } catch (IllegalStateException alreadyParked) {
                    return false;
                }
            });
        }
        long parked = runTogether(attempts).stream().filter(b -> b).count();

        assertEquals(1, parked);
        assertEquals(9, lot.getFreeCount(SpotType.MEDIUM));
    }

    @RepeatedTest(5)
    void ticketRedeemedAtManyGatesReleasesExactlyOnce() throws Exception {
        ParkingLot lot = new ParkingLot(List.of(new Level(1, 0, 2, 0)), new FirstAvailableStrategy());
        Ticket ticket = lot.park(new Car("C")).orElseThrow();

        List<Callable<Boolean>> redemptions = new ArrayList<>();
        for (int i = 0; i < GATES; i++) {
            redemptions.add(() -> {
                try {
                    lot.unpark(ticket.id());
                    return true;
                } catch (IllegalArgumentException usedTicket) {
                    return false;
                }
            });
        }
        long succeeded = runTogether(redemptions).stream().filter(b -> b).count();

        assertEquals(1, succeeded);
        assertEquals(2, lot.getFreeCount(SpotType.MEDIUM));
    }
}
