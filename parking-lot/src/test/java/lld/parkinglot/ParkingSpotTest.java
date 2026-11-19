package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ParkingSpotTest {

    private final ParkingSpot medium = new ParkingSpot("L1-M-001", 1, SpotType.MEDIUM);

    @Test
    void canFitIsAnExactSizeMatch() {
        assertTrue(medium.canFit(new Car("C")));
        assertFalse(medium.canFit(new Motorcycle("M")), "smaller vehicles do not fit larger spots");
        assertFalse(medium.canFit(new Truck("T")));
    }

    @Test
    void newSpotIsFree() {
        assertTrue(medium.isFree());
    }

    @Test
    void occupyThenVacate() {
        assertTrue(medium.tryOccupy(new Car("C")));
        assertFalse(medium.isFree());
        assertTrue(medium.vacate());
        assertTrue(medium.isFree());
    }

    @Test
    void cannotOccupyAnOccupiedSpot() {
        assertTrue(medium.tryOccupy(new Car("C1")));
        assertFalse(medium.tryOccupy(new Car("C2")));
    }

    @Test
    void cannotOccupyWithNonFittingVehicle() {
        assertFalse(medium.tryOccupy(new Truck("T")));
        assertTrue(medium.isFree());
    }

    @Test
    void vacatingAFreeSpotReturnsFalse() {
        assertFalse(medium.vacate());
    }

    @Test
    void exposesIdFloorTypeAndToString() {
        assertEquals("L1-M-001", medium.getId());
        assertEquals(1, medium.getFloor());
        assertEquals(SpotType.MEDIUM, medium.getType());
        assertEquals("L1-M-001", medium.toString());
    }

    @Test
    @Timeout(10)
    void exactlyOneThreadWinsARaceForTheSameSpot() {
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger winners = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                Vehicle car = new Car("C" + i);
                pool.submit(() -> {
                    start.await();
                    if (medium.tryOccupy(car)) {
                        winners.incrementAndGet();
                    }
                    return null;
                });
            }
            start.countDown();
        } // close() waits for every task
        assertEquals(1, winners.get());
    }
}
