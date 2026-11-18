package lld.parkinglot;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Facade for the whole system. Entry gates call park(), exit gates
 * call unpark(), display boards call the availability methods.
 * Any number of gates (threads) can call these at the same time.
 */
public class ParkingLot {
    private final List<Level> levels;
    private final SpotAssignmentStrategy strategy;

    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final Set<String> parkedPlates = ConcurrentHashMap.newKeySet();
    private final AtomicLong ticketSequence = new AtomicLong();

    public ParkingLot(List<Level> levels, SpotAssignmentStrategy strategy) {
        this.levels = List.copyOf(levels);
        this.strategy = strategy;
    }

    /**
     * Assigns a spot to the vehicle.
     *
     * @return a ticket, or empty if no suitable spot is free
     * @throws IllegalStateException if this vehicle is already parked
     */
    public Optional<Ticket> park(Vehicle vehicle) {
        // add() is atomic: only one thread can register a given plate.
        if (!parkedPlates.add(vehicle.getLicensePlate())) {
            throw new IllegalStateException(vehicle + " is already parked");
        }

        // Find-then-claim loop. The strategy suggests a spot; the claim
        // can fail if another gate took it in between, in which case we
        // simply ask for another suggestion.
        while (true) {
            Optional<ParkingSpot> candidate = strategy.findSpot(levels, vehicle);
            if (candidate.isEmpty()) {
                parkedPlates.remove(vehicle.getLicensePlate());
                return Optional.empty(); // lot is full for this vehicle type
            }
            ParkingSpot spot = candidate.get();
            if (levelOf(spot).tryOccupy(spot, vehicle)) {
                Ticket ticket = new Ticket(
                        "T-" + ticketSequence.incrementAndGet(), vehicle, spot, Instant.now());
                activeTickets.put(ticket.id(), ticket);
                return Optional.of(ticket);
            }
        }
    }

    /**
     * Releases the spot held by this ticket.
     *
     * @throws IllegalArgumentException if the ticket is unknown or already used
     */
    public void unpark(String ticketId) {
        // remove() is atomic, so a ticket can only be redeemed once even
        // if two exit gates receive it at the same moment.
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Unknown or already used ticket: " + ticketId);
        }
        ParkingSpot spot = ticket.spot();
        levelOf(spot).release(spot);
        parkedPlates.remove(ticket.vehicle().getLicensePlate());
    }

    /** Free spots of one type across the whole lot. */
    public int getFreeCount(SpotType type) {
        int total = 0;
        for (Level level : levels) {
            total += level.getFreeCount(type);
        }
        return total;
    }

    /** Snapshot of free spots: floor -> spot type -> count. */
    public Map<Integer, Map<SpotType, Integer>> getAvailability() {
        Map<Integer, Map<SpotType, Integer>> result = new TreeMap<>();
        for (Level level : levels) {
            Map<SpotType, Integer> perType = new EnumMap<>(SpotType.class);
            for (SpotType type : SpotType.values()) {
                perType.put(type, level.getFreeCount(type));
            }
            result.put(level.getFloor(), perType);
        }
        return result;
    }

    private Level levelOf(ParkingSpot spot) {
        for (Level level : levels) {
            if (level.getFloor() == spot.getFloor()) {
                return level;
            }
        }
        throw new IllegalStateException("Spot " + spot + " does not belong to this lot");
    }
}

