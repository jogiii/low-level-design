package lld.parkinglot;

import java.util.Objects;

public abstract class Vehicle {

    /**
     * Base class for all vehicles. Immutable: a vehicle's plate and type
     * never change, so it is safe to share between threads.
     */

    private final String licensePlate;
    private final VehicleType type;

    protected Vehicle(String licensePlate, VehicleType type) {
        this.licensePlate = Objects.requireNonNull(licensePlate, "licensePlate");
        this.type = Objects.requireNonNull(type, "type");
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public VehicleType getType() {
        return type;
    }

    @Override
    public String toString() {
        return type + "(" + licensePlate + ")";
    }
}
