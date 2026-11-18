package lld.parkinglot;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Level {
    private final int floor;
    private final List<ParkingSpot> spots;

    // The map itself is never modified after construction; only the
    // AtomicIntegers inside change, so it is safe to read concurrently.

    private final Map<SpotType, AtomicInteger> freeCount = new EnumMap<>(SpotType.class);

    public Level(int floor, int smallSpots, int mediumSpots, int largeSpots) {
        this.floor = floor;
        List<ParkingSpot> created = new ArrayList<>();
        addSpots(created, SpotType.SMALL, smallSpots);
        addSpots(created, SpotType.MEDIUM, mediumSpots);
        addSpots(created, SpotType.LARGE, largeSpots);
        this.spots = Collections.unmodifiableList(created);
    }

    private void addSpots(List<ParkingSpot> target, SpotType type, int count){
        for(int i=1;i<=count;i++){
            // e.g. "L2-M-007" = level 2, medium, spot 7
            String id = String.format("L%d-%s-%03d", floor, type.name().charAt(0), i);
            target.add(new ParkingSpot(id, floor, type));
        }
        freeCount.put(type, new AtomicInteger(count));
    }

    /** Claims the spot and keeps the counter in step with it. */
    public boolean tryOccupy(ParkingSpot spot, Vehicle vehicle) {
        if (spot.tryOccupy(vehicle)) {
            freeCount.get(spot.getType()).decrementAndGet();
            return true;
        }
        return false;
    }

    /** Frees the spot and keeps the counter in step with it. */
    public void release(ParkingSpot spot) {
        if (spot.vacate()) {
            freeCount.get(spot.getType()).incrementAndGet();
        }
    }

    public int getFreeCount(SpotType type) {
        return freeCount.get(type).get();
    }

    public int getFloor() {
        return floor;
    }

    public List<ParkingSpot> getSpots() {
        return spots;
    }
}
