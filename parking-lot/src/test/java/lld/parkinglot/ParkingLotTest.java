package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ParkingLotTest {

    private static ParkingLot lot(Level... levels) {
        return new ParkingLot(List.of(levels), new FirstAvailableStrategy());
    }

    // --- construction ---

    @Test
    void rejectsDuplicateFloorNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> lot(new Level(1, 1, 1, 1), new Level(1, 1, 1, 1)));
    }

    @Test
    void rejectsNullStrategy() {
        assertThrows(NullPointerException.class,
                () -> new ParkingLot(List.of(new Level(1, 1, 1, 1)), null));
    }

    // --- park ---

    @Test
    void parkReturnsTicketForTheVehicleAndOccupiesSpot() {
        ParkingLot lot = lot(new Level(1, 1, 1, 1));
        Vehicle car = new Car("C");

        Ticket ticket = lot.park(car).orElseThrow();

        assertSame(car, ticket.vehicle());
        assertEquals(SpotType.MEDIUM, ticket.spot().getType());
        assertFalse(ticket.spot().isFree());
        assertEquals(0, lot.getFreeCount(SpotType.MEDIUM));
        assertTrue(ticket.id().startsWith("T-"));
        assertNotNull(ticket.entryTime());
    }

    @Test
    void ticketIdsAreUnique() {
        ParkingLot lot = lot(new Level(1, 0, 2, 0));
        Ticket a = lot.park(new Car("A")).orElseThrow();
        Ticket b = lot.park(new Car("B")).orElseThrow();
        assertNotEquals(a.id(), b.id());
    }

    @Test
    void parkReturnsEmptyWhenFullForThatVehicleType() {
        ParkingLot lot = lot(new Level(1, 1, 1, 0));
        assertTrue(lot.park(new Truck("T")).isEmpty());
        // Other types are unaffected.
        assertTrue(lot.park(new Car("C")).isPresent());
        assertTrue(lot.park(new Motorcycle("M")).isPresent());
    }

    @Test
    void parkRejectsNullVehicle() {
        ParkingLot lot = lot(new Level(1, 1, 1, 1));
        assertThrows(NullPointerException.class, () -> lot.park(null));
    }

    @Test
    void parkingSamePlateTwiceIsRejected() {
        ParkingLot lot = lot(new Level(1, 0, 2, 0));
        lot.park(new Car("DUP"));
        assertThrows(IllegalStateException.class, () -> lot.park(new Car("DUP")));
        assertEquals(1, lot.getFreeCount(SpotType.MEDIUM));
    }

    @Test
    void plateIsReleasedWhenLotIsFull() {
        ParkingLot lot = lot(new Level(1, 0, 1, 0));
        Ticket blocker = lot.park(new Car("BLOCKER")).orElseThrow();

        assertTrue(lot.park(new Car("WAITING")).isEmpty());

        lot.unpark(blocker.id());
        // Would throw "already parked" if the failed attempt leaked the plate.
        assertTrue(lot.park(new Car("WAITING")).isPresent());
    }

    @Test
    void strategySuggestingNonFittingSpotFailsFastAndReleasesPlate() {
        Level level = new Level(1, 1, 1, 0);
        ParkingSpot smallSpot = level.getSpots(SpotType.SMALL).get(0);
        SpotAssignmentStrategy buggy = (levels, vehicle) -> Optional.of(smallSpot);
        ParkingLot lot = new ParkingLot(List.of(level), buggy);

        assertThrows(IllegalStateException.class, () -> lot.park(new Car("C")));
        assertTrue(smallSpot.isFree());
        // Plate must not leak: a second attempt hits the same strategy error, not "already parked".
        IllegalStateException again = assertThrows(IllegalStateException.class, () -> lot.park(new Car("C")));
        assertFalse(again.getMessage().contains("already parked"));
    }

    @Test
    void spotFromAnotherLotIsRejected() {
        ParkingSpot foreign = new ParkingSpot("L9-M-001", 9, SpotType.MEDIUM);
        ParkingLot lot = new ParkingLot(List.of(new Level(1, 0, 1, 0)),
                (levels, vehicle) -> Optional.of(foreign));
        assertThrows(IllegalStateException.class, () -> lot.park(new Car("C")));
        assertTrue(foreign.isFree());
    }

    @Test
    void retriesWhenSuggestedSpotWasAlreadyTaken() {
        Level level = new Level(1, 0, 2, 0);
        ParkingSpot first = level.getSpots(SpotType.MEDIUM).get(0);
        ParkingSpot second = level.getSpots(SpotType.MEDIUM).get(1);
        level.tryOccupy(first, new Car("SOMEONE-ELSE"));

        // Stale strategy: suggests the taken spot once, then a free one.
        int[] calls = {0};
        SpotAssignmentStrategy stale = (levels, vehicle) -> Optional.of(calls[0]++ == 0 ? first : second);
        ParkingLot lot = new ParkingLot(List.of(level), stale);

        Ticket ticket = lot.park(new Car("C")).orElseThrow();
        assertSame(second, ticket.spot());
        assertEquals(2, calls[0]);
    }

    // --- unpark ---

    @Test
    void unparkFreesSpotAndRestoresCount() {
        ParkingLot lot = lot(new Level(1, 0, 1, 0));
        Ticket ticket = lot.park(new Car("C")).orElseThrow();

        lot.unpark(ticket.id());

        assertTrue(ticket.spot().isFree());
        assertEquals(1, lot.getFreeCount(SpotType.MEDIUM));
    }

    @Test
    void unparkAllowsSamePlateToParkAgain() {
        ParkingLot lot = lot(new Level(1, 0, 1, 0));
        lot.unpark(lot.park(new Car("C")).orElseThrow().id());
        assertDoesNotThrow(() -> lot.park(new Car("C")).orElseThrow());
    }

    @Test
    void ticketCannotBeRedeemedTwice() {
        ParkingLot lot = lot(new Level(1, 0, 1, 0));
        Ticket ticket = lot.park(new Car("C")).orElseThrow();
        lot.unpark(ticket.id());
        assertThrows(IllegalArgumentException.class, () -> lot.unpark(ticket.id()));
        assertEquals(1, lot.getFreeCount(SpotType.MEDIUM));
    }

    @Test
    void unknownTicketIsRejected() {
        ParkingLot lot = lot(new Level(1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> lot.unpark("T-404"));
    }

    // --- availability ---

    @Test
    void freeCountSumsAcrossLevels() {
        ParkingLot lot = lot(new Level(1, 1, 2, 3), new Level(2, 4, 5, 6));
        assertEquals(5, lot.getFreeCount(SpotType.SMALL));
        assertEquals(7, lot.getFreeCount(SpotType.MEDIUM));
        assertEquals(9, lot.getFreeCount(SpotType.LARGE));
    }

    @Test
    void availabilityIsPerFloorSortedByFloor() {
        ParkingLot lot = lot(new Level(3, 1, 1, 1), new Level(1, 2, 2, 2));
        lot.park(new Car("C")); // goes to floor 3, the first level in the list

        Map<Integer, Map<SpotType, Integer>> availability = lot.getAvailability();

        assertEquals(List.of(1, 3), List.copyOf(availability.keySet()));
        assertEquals(Map.of(SpotType.SMALL, 2, SpotType.MEDIUM, 2, SpotType.LARGE, 2), availability.get(1));
        assertEquals(Map.of(SpotType.SMALL, 1, SpotType.MEDIUM, 0, SpotType.LARGE, 1), availability.get(3));
    }
}
