package lld.parkinglot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VehicleTest {

    @Test
    void subclassesReportTheirType() {
        assertEquals(VehicleType.CAR, new Car("C").getType());
        assertEquals(VehicleType.MOTORCYCLE, new Motorcycle("M").getType());
        assertEquals(VehicleType.TRUCK, new Truck("T").getType());
    }

    @Test
    void keepsLicensePlateAndFormatsToString() {
        Vehicle car = new Car("KA-01");
        assertEquals("KA-01", car.getLicensePlate());
        assertEquals("CAR(KA-01)", car.toString());
    }

    @Test
    void rejectsNullLicensePlate() {
        assertThrows(NullPointerException.class, () -> new Car(null));
    }
}
