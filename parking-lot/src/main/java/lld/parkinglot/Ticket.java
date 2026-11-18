package lld.parkinglot;

import java.time.Instant;

/**
 * Proof of parking, handed out at entry and presented at exit.
 * It remembers the spot, so exiting never needs a search.
 * A record is immutable, which makes it safe to share across threads.
 */
public record Ticket(String id, Vehicle vehicle, ParkingSpot spot, Instant entryTime) {
}
