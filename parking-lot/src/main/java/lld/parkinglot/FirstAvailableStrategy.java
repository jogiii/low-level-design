package lld.parkinglot;

import java.util.List;
import java.util.Optional;

/** Picks the first free, fitting spot, starting from the lowest level. */
public class FirstAvailableStrategy implements SpotAssignmentStrategy {

    @Override
    public Optional<ParkingSpot> findSpot(List<Level> levels, Vehicle vehicle) {
        SpotType needed = SpotType.forVehicle(vehicle.getType());
        for (Level level : levels) {
            if (level.getFreeCount(needed) == 0) {
                continue; // cheap skip of a full level
            }
            for (ParkingSpot spot : level.getSpots()) {
                if (spot.canFit(vehicle) && spot.isFree()) {
                    return Optional.of(spot);
                }
            }
        }
        return Optional.empty();
    }
}