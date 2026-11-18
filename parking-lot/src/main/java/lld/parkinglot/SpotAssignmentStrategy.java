package lld.parkinglot;

import java.util.List;
import java.util.Optional;

/**
 * Strategy pattern: the policy for choosing a spot is pluggable.
 * Implementations only SUGGEST a free spot; they do not claim it.
 * ParkingLot does the claim and asks again if another gate got there
 * first. Implementations must be safe to call from several threads.
 */
public interface SpotAssignmentStrategy {
    Optional<ParkingSpot> findSpot(List<Level> levels, Vehicle vehicle);
}
