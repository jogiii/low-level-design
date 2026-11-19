package lld.atm;

import java.util.Map;

/**
 * State pattern. Each state overrides only the actions that make sense in
 * it; everything else falls through to these defaults and is rejected.
 * That replaces an if/else on "current state" inside every Atm method.
 *
 * States hold no data (everything lives in Atm), so each is a singleton.
 * Sealed: the full state machine is visible in this one line.
 */
sealed interface AtmState permits IdleState, CardInsertedState, AuthenticatedState {

    String name();

    default void insertCard(Atm atm, Card card) {
        throw invalid("insert a card");
    }

    default void enterPin(Atm atm, String pin) {
        throw invalid("enter a PIN");
    }

    default Transaction checkBalance(Atm atm) {
        throw invalid("check balance");
    }

    default Transaction withdraw(Atm atm, long amount) {
        throw invalid("withdraw");
    }

    default Transaction deposit(Atm atm, Map<Denomination, Integer> notes) {
        throw invalid("deposit");
    }

    default void ejectCard(Atm atm) {
        throw invalid("eject a card");
    }

    private IllegalStateException invalid(String action) {
        return new IllegalStateException("Cannot " + action + " while ATM is " + name());
    }
}
