package lld.atm;

import java.util.Map;
import java.util.Objects;

/**
 * PIN accepted. The customer may run any number of transactions, then eject.
 * A failed transaction (say, insufficient funds) keeps the session open so
 * they can try a smaller amount.
 */
final class AuthenticatedState implements AtmState {
    static final AuthenticatedState INSTANCE = new AuthenticatedState();

    private AuthenticatedState() {
    }

    @Override
    public String name() {
        return "AUTHENTICATED";
    }

    @Override
    public Transaction checkBalance(Atm atm) {
        long balance = atm.bank().getBalance(atm.token());
        return atm.record(TransactionType.BALANCE_ENQUIRY, 0, Map.of(), balance);
    }

    /**
     * Order matters, and each step can fail without leaving a mess:
     *  1. validate the amount           - nothing has happened yet
     *  2. plan the notes                - no cash moved, account untouched
     *  3. debit the account at the bank - if refused, no cash moved
     *  4. dispense the planned notes    - should not fail: the plan was made under
     *                                     the same Atm lock, nothing changed since.
     *                                     If it does (a jam, in real life), the
     *                                     debit is reversed: a compensating transaction.
     */
    @Override
    public Transaction withdraw(Atm atm, long amount) {
        requireValidAmount(amount);
        Map<Denomination, Integer> notes = atm.dispenser().plan(amount)
                .orElseThrow(() -> new AtmException(AtmException.Reason.CASH_UNAVAILABLE,
                        "This ATM cannot dispense " + amount + " right now; try a different amount"));
        long balanceAfter = atm.bank().withdraw(atm.token(), amount);
        try {
            atm.dispenser().dispense(notes);
        } catch (RuntimeException e) {
            atm.bank().deposit(atm.token(), amount); // give the money back
            throw e;
        }
        return atm.record(TransactionType.WITHDRAWAL, amount, notes, balanceAfter);
    }

    /** Credit the bank first; only then keep the notes. If the bank refuses, the notes go back. */
    @Override
    public Transaction deposit(Atm atm, Map<Denomination, Integer> notes) {
        Objects.requireNonNull(notes, "notes");
        if (notes.values().stream().anyMatch(n -> n == null || n < 0)) {
            throw new AtmException(AtmException.Reason.INVALID_AMOUNT, "Note counts must be >= 0");
        }
        long amount = CashDispenser.valueOf(notes);
        if (amount <= 0) {
            throw new AtmException(AtmException.Reason.INVALID_AMOUNT, "Deposit at least one note");
        }
        long balanceAfter = atm.bank().deposit(atm.token(), amount);
        atm.dispenser().load(notes);
        return atm.record(TransactionType.DEPOSIT, amount, notes, balanceAfter);
    }

    @Override
    public void ejectCard(Atm atm) {
        atm.returnCard();
    }

    private static void requireValidAmount(long amount) {
        int step = Denomination.smallest();
        if (amount <= 0 || amount % step != 0) {
            throw new AtmException(AtmException.Reason.INVALID_AMOUNT,
                    "Amount must be a positive multiple of " + step + ", was " + amount);
        }
    }
}
