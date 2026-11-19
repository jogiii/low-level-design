# ATM (LLD in Java)

Requires JDK 25 (matches the Gradle toolchain in `build.gradle`).

    javac -d out src/main/java/lld/atm/*.java
    java -cp out lld.atm.Main

Or build and run the tests with Gradle:

    ./gradlew build

## Requirements covered

- Insert card -> enter PIN -> balance enquiry / cash withdrawal / cash deposit -> eject card
- Card checks at insert: unknown card, forged card (real number with wrong account), expired card, blocked card
- 3 wrong PINs block the card, and the ATM keeps (captures) it
- Withdrawal amount must be a multiple of 100; notes are 500 / 200 / 100, largest first
- Per-account daily withdrawal limit
- The customer is never debited for cash the ATM can't give, and the ATM never pays out cash the bank refused to debit
- Many ATMs share one bank: two holders of a joint account withdrawing at the same instant can never overdraw it
- Operator can refill cash, but only between customers

## Suggested reading order

1. `Denomination`, `Card`, `AuthToken`, `AtmException`: the vocabulary
2. `Account`: balance and daily limit; the per-account lock
3. `BankService`, `Bank`: card checks, PIN attempts, sessions
4. `NoteDispenser`, `CashDispenser`: Chain of Responsibility for notes; plan vs dispense
5. `AtmState`, `IdleState`, `CardInsertedState`, `AuthenticatedState`: the State pattern
6. `Atm`: the facade (and State-pattern context) the customer talks to
7. `Transaction`, `TransactionType`: receipts
8. `Main`: demo, including two ATMs racing on one joint account
9. `src/test/java/lld/atm/AtmTest.java`: every rule above as a test

`CLASS-DIAGRAM.md` has the class diagram, state diagram, withdrawal sequence diagram and design notes.
