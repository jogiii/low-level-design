package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LevelTest {

    @Test
    void createsSpotsWithFormattedIds() {
        Level level = new Level(2, 1, 2, 1);
        List<String> ids = level.getSpots().stream().map(ParkingSpot::getId).toList();
        assertEquals(List.of("L2-S-001", "L2-M-001", "L2-M-002", "L2-L-001"), ids);
        assertEquals(2, level.getFloor());
        level.getSpots().forEach(s -> assertEquals(2, s.getFloor()));
    }

    @Test
    void getSpotsByTypeReturnsOnlyThatType() {
        Level level = new Level(1, 3, 2, 1);
        assertEquals(3, level.getSpots(SpotType.SMALL).size());
        assertEquals(2, level.getSpots(SpotType.MEDIUM).size());
        assertEquals(1, level.getSpots(SpotType.LARGE).size());
        level.getSpots(SpotType.MEDIUM).forEach(s -> assertEquals(SpotType.MEDIUM, s.getType()));
    }

    @Test
    void spotListsAreUnmodifiable() {
        Level level = new Level(1, 1, 1, 1);
        assertThrows(UnsupportedOperationException.class, () -> level.getSpots().clear());
        assertThrows(UnsupportedOperationException.class, () -> level.getSpots(SpotType.SMALL).clear());
    }

    @Test
    void zeroSpotsOfATypeIsAllowed() {
        Level level = new Level(1, 0, 0, 0);
        assertTrue(level.getSpots().isEmpty());
        assertTrue(level.getSpots(SpotType.LARGE).isEmpty());
        assertEquals(0, level.getFreeCount(SpotType.LARGE));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(IllegalArgumentException.class, () -> new Level(1, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Level(1, 0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new Level(1, 0, 0, -1));
    }

    @Test
    void freeCountStartsAtCapacity() {
        Level level = new Level(1, 5, 10, 2);
        assertEquals(5, level.getFreeCount(SpotType.SMALL));
        assertEquals(10, level.getFreeCount(SpotType.MEDIUM));
        assertEquals(2, level.getFreeCount(SpotType.LARGE));
    }

    @Test
    void tryOccupyAndReleaseKeepCounterInStep() {
        Level level = new Level(1, 0, 2, 0);
        ParkingSpot spot = level.getSpots(SpotType.MEDIUM).get(0);

        assertTrue(level.tryOccupy(spot, new Car("C")));
        assertEquals(1, level.getFreeCount(SpotType.MEDIUM));

        level.release(spot);
        assertEquals(2, level.getFreeCount(SpotType.MEDIUM));
    }

    @Test
    void failedOccupyDoesNotChangeCounter() {
        Level level = new Level(1, 0, 1, 0);
        ParkingSpot spot = level.getSpots(SpotType.MEDIUM).get(0);
        assertTrue(level.tryOccupy(spot, new Car("C1")));

        assertFalse(level.tryOccupy(spot, new Car("C2")));
        assertEquals(0, level.getFreeCount(SpotType.MEDIUM));
    }

    @Test
    void releasingAFreeSpotDoesNotInflateCounter() {
        Level level = new Level(1, 0, 1, 0);
        level.release(level.getSpots(SpotType.MEDIUM).get(0));
        assertEquals(1, level.getFreeCount(SpotType.MEDIUM));
    }
}
