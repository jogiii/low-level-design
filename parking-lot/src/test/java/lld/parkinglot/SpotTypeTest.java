package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpotTypeTest {

    @Test
    void mapsEachVehicleTypeToExactlyOneSpotType() {
        assertEquals(SpotType.SMALL, SpotType.forVehicle(VehicleType.MOTORCYCLE));
        assertEquals(SpotType.MEDIUM, SpotType.forVehicle(VehicleType.CAR));
        assertEquals(SpotType.LARGE, SpotType.forVehicle(VehicleType.TRUCK));
    }
}
