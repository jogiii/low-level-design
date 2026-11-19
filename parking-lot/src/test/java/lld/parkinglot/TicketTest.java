package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TicketTest {
    private final Vehicle car = new Car("C");
    private final ParkingSpot spot = new ParkingSpot("L1-M-001", 1, SpotType.MEDIUM);
    private final Instant now = Instant.now();

    @Test
    void rejectsNullComponents() {
        assertThrows(NullPointerException.class, () -> new Ticket(null, car, spot, now));
        assertThrows(NullPointerException.class, () -> new Ticket("T-1", null, spot, now));
        assertThrows(NullPointerException.class, () -> new Ticket("T-1", car, null, now));
        assertThrows(NullPointerException.class, () -> new Ticket("T-1", car, spot, null));
    }
}
