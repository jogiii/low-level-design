# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository layout

A collection of low-level-design (LLD) exercises in Java. Each problem is a self-contained Gradle project in its own top-level directory (currently only `parking-lot/`), with its own `README.md` (reading order) and `CLASS-DIAGRAM.md` (Mermaid class diagram plus design notes, written in Hinglish). There is no root build; run commands from inside the problem's directory.

## Commands (from `parking-lot/`)

```sh
./gradlew build          # compile + test (Gradle 9.1 wrapper, Java 25 toolchain)
./gradlew test           # JUnit 5; no tests exist yet, src/test/java would hold them
./gradlew test --tests 'lld.parkinglot.SomeTest.someMethod'   # single test
```

There is no `application` plugin, so run the demo with plain `javac`/`java`:

```sh
javac -d out src/main/java/lld/parkinglot/*.java
java -cp out lld.parkinglot.Main
```

## Parking lot architecture

Three layers, each with one job: `ParkingLot` (orchestration/facade) → `Level` (free-spot counting) → `ParkingSpot` (locking).

- **Concurrency is the core design concern.** Multiple gates (threads) call `park`/`unpark` concurrently, and `Main` includes a demo of gates racing for spots. Mutable state lives in only a few places:
  - `ParkingSpot.parkedVehicle`: guarded by the spot's own `synchronized` methods. The spot is the unit of locking, so different spots never contend.
  - `Level.freeCount`: an `EnumMap` of `AtomicInteger`s, built once and never structurally modified. Always change occupancy through `Level.tryOccupy`/`Level.release`, not `ParkingSpot` directly, or the counters drift.
  - `ParkingLot.activeTickets` / `parkedPlates`: concurrent collections whose atomic `add`/`remove` prevent double-parking a plate or redeeming a ticket twice.
- **Find-then-claim loop** in `ParkingLot.park`: the strategy only *suggests* a spot (it may be stale); the claim via `tryOccupy` can fail, in which case the loop asks again. If no spot is found, the plate is removed from `parkedPlates` before returning empty.
- **Strategy pattern**: spot selection goes through the `SpotAssignmentStrategy` interface (`FirstAvailableStrategy` is the only implementation). Add a new selection policy as a new class, not by editing `ParkingLot`.
- **Vehicle → spot mapping** lives only in `SpotType.forVehicle` (MOTORCYCLE→SMALL, CAR→MEDIUM, TRUCK→LARGE). It is an exact match, not "fits in anything larger." `ParkingSpot.canFit` delegates to it.
- `ParkingLot.levelOf` finds a spot's level by matching floor numbers, so floor numbers must be unique across levels.
- `Ticket` is an immutable record; spot IDs are formatted `L<floor>-<S|M|L>-<nnn>`.

When the design changes, update `CLASS-DIAGRAM.md` to match.
