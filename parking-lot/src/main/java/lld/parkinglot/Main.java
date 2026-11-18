package lld.parkinglot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Demo: a basic walk-through, then several gates racing for spots. */
public class Main {

    public static void main(String[] args) throws Exception {
        // Two levels, each with 5 small, 10 medium and 2 large spots.
        ParkingLot lot = new ParkingLot(
                List.of(new Level(1, 5, 10, 2), new Level(2, 5, 10, 2)),
                new FirstAvailableStrategy());

        basicFlow(lot);
        concurrentGates(lot);
    }

    private static void basicFlow(ParkingLot lot) {
        System.out.println("=== Basic flow ===");
        System.out.println("Availability at start: " + lot.getAvailability());

        Ticket carTicket = lot.park(new Car("CAR-001")).orElseThrow();
        Ticket bikeTicket = lot.park(new Motorcycle("BIKE-001")).orElseThrow();
        Ticket truckTicket = lot.park(new Truck("TRUCK-001")).orElseThrow();
        System.out.println("Car parked at   " + carTicket.spot());
        System.out.println("Bike parked at  " + bikeTicket.spot());
        System.out.println("Truck parked at " + truckTicket.spot());
        System.out.println("Availability now:      " + lot.getAvailability());

        try {
            lot.park(new Car("CAR-001"));
        } catch (IllegalStateException e) {
            System.out.println("Rejected duplicate entry: " + e.getMessage());
        }

        lot.unpark(carTicket.id());
        lot.unpark(bikeTicket.id());
        lot.unpark(truckTicket.id());

        try {
            lot.unpark(carTicket.id());
        } catch (IllegalArgumentException e) {
            System.out.println("Rejected reused ticket:   " + e.getMessage());
        }
        System.out.println("Availability after exit: " + lot.getAvailability());
    }

    private static void concurrentGates(ParkingLot lot) throws Exception {
        System.out.println();
        System.out.println("=== 4 entry gates, 50 cars, 20 car spots ===");

        ExecutorService gates = Executors.newFixedThreadPool(4);
        List<Callable<Optional<Ticket>>> arrivals = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Vehicle car = new Car("RACE-" + i);
            arrivals.add(() -> lot.park(car));
        }

        List<Ticket> tickets = new ArrayList<>();
        int turnedAway = 0;
        for (Future<Optional<Ticket>> result : gates.invokeAll(arrivals)) {
            Optional<Ticket> ticket = result.get();
            if (ticket.isPresent()) {
                tickets.add(ticket.get());
            } else {
                turnedAway++;
            }
        }

        // If locking were broken, two cars would share a spot and this
        // set would be smaller than the ticket list.
        Set<String> distinctSpots = new HashSet<>();
        for (Ticket ticket : tickets) {
            distinctSpots.add(ticket.spot().getId());
        }
        System.out.println("Cars parked:        " + tickets.size());
        System.out.println("Cars turned away:   " + turnedAway);
        System.out.println("Distinct spots used: " + distinctSpots.size());
        System.out.println("Free medium spots:  " + lot.getFreeCount(SpotType.MEDIUM));

        // Everyone leaves through the exit gates at the same time.
        List<Callable<Void>> departures = new ArrayList<>();
        for (Ticket ticket : tickets) {
            departures.add(() -> {
                lot.unpark(ticket.id());
                return null;
            });
        }
        for (Future<Void> done : gates.invokeAll(departures)) {
            done.get();
        }
        gates.shutdown();

        System.out.println("Availability after all exits: " + lot.getAvailability());
    }
}

