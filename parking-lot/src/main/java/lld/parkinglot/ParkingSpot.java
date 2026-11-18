package lld.parkinglot;

public class ParkingSpot {

    /**
     * A single parking spot. This is the unit of locking in the design:
     * each spot guards its own state, so two gates working on different
     * spots never block each other.
     */
    private final String id;
    private final int floor;
    private final SpotType type;

    // Guarded by "this". null means the spot is free.
    private Vehicle parkedVehicle;

    public ParkingSpot(String id, int floor, SpotType type) {
        this.id = id;
        this.floor = floor;
        this.type = type;
    }

    /** Pure size check; says nothing about whether the spot is free. */

    public boolean canFit(Vehicle vehicle) {
        return type == SpotType.forVehicle(vehicle.getType());
    }

    /**
     * Atomically claims the spot. The check ("is it free?") and the
     * write ("now it's mine") happen under one lock, so if two gates
     * race for the same spot exactly one of them gets true.
     */

    public synchronized boolean tryOccupy(Vehicle vehicle) {
        if (parkedVehicle != null || !canFit(vehicle)) {
            return false;
        }
        parkedVehicle = vehicle;
        return true;
    }

    public synchronized boolean vacate(){
        if(parkedVehicle == null){
            return false;
        }
        parkedVehicle = null;
        return true;
    }

    public synchronized boolean isFree() {
        return parkedVehicle == null;
    }
    public String getId() {
        return id;
    }

    public int getFloor() {
        return floor;
    }

    public SpotType getType() {
        return type;
    }

    @Override
    public String toString() {
        return id;
    }
}
