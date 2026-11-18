# Parking Lot (LLD in Java)

Requires JDK 25 (matches the Gradle toolchain in `build.gradle`).

    javac -d out src/main/java/lld/parkinglot/*.java
    java -cp out lld.parkinglot.Main

Or build with Gradle:

    ./gradlew build

Suggested reading order:

1. `VehicleType`, `SpotType` - the vocabulary, and the vehicle-to-spot rule
2. `Vehicle`, `Car`, `Motorcycle`, `Truck`
3. `ParkingSpot` - the atomic claim (`tryOccupy`)
4. `Level` - spots plus O(1) free counters
5. `Ticket`
6. `SpotAssignmentStrategy`, `FirstAvailableStrategy`
7. `ParkingLot` - the facade the gates call
8. `Main` - demo, including four gates racing for spots
