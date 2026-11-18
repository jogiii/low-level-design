package lld.parkinglot;

/**
 * Size of a parking spot. Kept separate from VehicleType on purpose:
 * the rule "which vehicle goes in which spot" lives in ONE place
 * (forVehicle), so changing it never touches the rest of the code.
 */
public enum SpotType {
    SMALL,
    MEDIUM,
    LARGE;

    /** The spot size a given vehicle type needs. */
    public static SpotType forVehicle(VehicleType vehicleType) {
        return switch (vehicleType){
            case MOTORCYCLE -> SMALL;
            case CAR -> MEDIUM;
            case TRUCK -> LARGE;
        };
    }
}
