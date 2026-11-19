package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FirstAvailableStrategyTest {

    private final SpotAssignmentStrategy strategy = new FirstAvailableStrategy();

    @Test
    void picksFirstSpotOfMatchingTypeOnLowestLevel() {
        List<Level> levels = List.of(new Level(1, 1, 2, 1), new Level(2, 1, 2, 1));
        assertEquals("L1-M-001", strategy.findSpot(levels, new Car("C")).orElseThrow().getId());
    }

    @Test
    void skipsOccupiedSpots() {
        Level level = new Level(1, 0, 2, 0);
        level.tryOccupy(level.getSpots(SpotType.MEDIUM).get(0), new Car("C1"));
        assertEquals("L1-M-002", strategy.findSpot(List.of(level), new Car("C2")).orElseThrow().getId());
    }

    @Test
    void movesToNextLevelWhenLowerLevelIsFull() {
        Level first = new Level(1, 0, 1, 0);
        Level second = new Level(2, 0, 1, 0);
        first.tryOccupy(first.getSpots(SpotType.MEDIUM).get(0), new Car("C1"));
        assertEquals("L2-M-001",
                strategy.findSpot(List.of(first, second), new Car("C2")).orElseThrow().getId());
    }

    @Test
    void returnsSpotMatchingVehicleSize() {
        List<Level> levels = List.of(new Level(1, 1, 1, 1));
        assertEquals(SpotType.SMALL, strategy.findSpot(levels, new Motorcycle("M")).orElseThrow().getType());
        assertEquals(SpotType.LARGE, strategy.findSpot(levels, new Truck("T")).orElseThrow().getType());
    }

    @Test
    void emptyWhenNoFittingSpotIsFree() {
        // Plenty of small and medium spots, but no large ones.
        List<Level> levels = List.of(new Level(1, 5, 5, 0));
        assertTrue(strategy.findSpot(levels, new Truck("T")).isEmpty());
    }

    @Test
    void emptyWhenThereAreNoLevels() {
        assertTrue(strategy.findSpot(List.of(), new Car("C")).isEmpty());
    }
}
