# Parking Lot — Class Diagram

```mermaid
classDiagram
    direction TB

    class ParkingLot {
        -List~Level~ levels
        -SpotAssignmentStrategy strategy
        -Map~String, Ticket~ activeTickets
        -Set~String~ parkedPlates
        -AtomicLong ticketSequence
        +park(Vehicle) Optional~Ticket~
        +unpark(String ticketId) void
        +getFreeCount(SpotType) int
        +getAvailability() Map
    }

    class Level {
        -int floor
        -List~ParkingSpot~ spots
        -Map~SpotType, AtomicInteger~ freeCount
        +tryOccupy(ParkingSpot, Vehicle) boolean
        +release(ParkingSpot) void
        +getFreeCount(SpotType) int
        +getFloor() int
        +getSpots() List~ParkingSpot~
    }

    class ParkingSpot {
        -String id
        -int floor
        -SpotType type
        -Vehicle parkedVehicle
        +canFit(Vehicle) boolean
        +tryOccupy(Vehicle) boolean  «synchronized»
        +vacate() boolean  «synchronized»
        +isFree() boolean  «synchronized»
    }

    class Vehicle {
        <<abstract>>
        -String licensePlate
        -VehicleType type
        +getLicensePlate() String
        +getType() VehicleType
    }

    class Car
    class Motorcycle
    class Truck

    class Ticket {
        <<record>>
        +String id
        +Vehicle vehicle
        +ParkingSpot spot
        +Instant entryTime
    }

    class SpotAssignmentStrategy {
        <<interface>>
        +findSpot(List~Level~, Vehicle) Optional~ParkingSpot~
    }

    class FirstAvailableStrategy {
        +findSpot(List~Level~, Vehicle) Optional~ParkingSpot~
    }

    class VehicleType {
        <<enumeration>>
        CAR
        MOTORCYCLE
        TRUCK
    }

    class SpotType {
        <<enumeration>>
        SMALL
        MEDIUM
        LARGE
        +forVehicle(VehicleType)$ SpotType
    }

    %% Relationships
    ParkingLot "1" *-- "many" Level : composition
    Level "1" *-- "many" ParkingSpot : composition
    ParkingLot --> SpotAssignmentStrategy : uses (Strategy)
    SpotAssignmentStrategy <|.. FirstAvailableStrategy : implements
    ParkingLot --> Ticket : creates
    Ticket --> Vehicle : refers
    Ticket --> ParkingSpot : refers
    Vehicle <|-- Car
    Vehicle <|-- Motorcycle
    Vehicle <|-- Truck
    Vehicle --> VehicleType : has
    ParkingSpot --> SpotType : has
    ParkingSpot --> Vehicle : holds (nullable)
    SpotType ..> VehicleType : forVehicle()
```

## Relationships ka matlab (Hinglish)

| Relationship | Symbol | Matlab |
|---|---|---|
| `ParkingLot *-- Level` | Composition (solid diamond) | Levels ParkingLot ke **part** hain; lot ke bina level ka koi matlab nahi |
| `Level *-- ParkingSpot` | Composition | Spots level ke andar hi bante aur rehte hain |
| `ParkingLot --> SpotAssignmentStrategy` | Dependency/Association | Lot strategy ko **use** karta hai, own nahi karta — kal badal sakte ho |
| `FirstAvailableStrategy ..|> SpotAssignmentStrategy` | Realization (dashed) | Interface ka implementation — **Strategy pattern** |
| `Vehicle <|-- Car/Motorcycle/Truck` | Inheritance | "is-a" relationship |
| `Ticket --> Spot/Vehicle` | Association | Ticket sirf **reference** rakhta hai, ownership nahi |

## Key design points diagram se

1. **Layering:** `ParkingLot → Level → ParkingSpot` — har layer apna kaam karti hai.
   Lot = orchestration, Level = counting, Spot = locking.
2. **Strategy pattern:** `ParkingLot` interface pe depend karta hai, concrete class pe nahi
   (Dependency Inversion). Nayi strategy = nayi class, purana code untouched (Open/Closed).
3. **Enums ka bridge:** `SpotType.forVehicle()` hi ek jagah hai jahan
   VehicleType aur SpotType judte hain.
4. **Mutable state sirf 2 jagah hai:** `ParkingSpot.parkedVehicle` (synchronized) aur
   `Level.freeCount` (AtomicInteger). Baaki sab immutable — isliye thread-safe.

