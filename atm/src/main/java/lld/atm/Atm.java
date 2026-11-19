package lld.atm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Facade for one physical machine, and the "context" of the State pattern.
 * Every public method just hands the call to the current state.
 *
 * Locking: all public methods are synchronized on the Atm. One machine has
 * one customer at a time, but the operator's refill or a stray retry can
 * still arrive on another thread, and a withdrawal's plan-debit-dispense
 * steps must not interleave with anything that touches the cash.
 * Lock order is always Atm -> (CashDispenser | Bank internals), and the bank
 * never calls back into an Atm, so the locks cannot deadlock.
 */
public class Atm {
    private final String id;
    private final BankService bank;
    private final CashDispenser dispenser;

    // All guarded by "this".
    private AtmState state = IdleState.INSTANCE;
    private Card card;
    private AuthToken token;
    private long transactionSequence;
    private final List<Card> retainedCards = new ArrayList<>();

    public Atm(String id, BankService bank, CashDispenser dispenser) {
        this.id = Objects.requireNonNull(id, "id");
        this.bank = Objects.requireNonNull(bank, "bank");
        this.dispenser = Objects.requireNonNull(dispenser, "dispenser");
    }

    // ------------------------------------------------ customer actions

    public synchronized void insertCard(Card card) {
        state.insertCard(this, card);
    }

    public synchronized void enterPin(String pin) {
        state.enterPin(this, pin);
    }

    public synchronized Transaction checkBalance() {
        return state.checkBalance(this);
    }

    public synchronized Transaction withdraw(long amount) {
        return state.withdraw(this, amount);
    }

    public synchronized Transaction deposit(Map<Denomination, Integer> notes) {
        return state.deposit(this, notes);
    }

    public synchronized void ejectCard() {
        state.ejectCard(this);
    }

    // ------------------------------------------------ operator actions

    /** Load notes into the cassettes. Only between customers. */
    public synchronized void refill(Map<Denomination, Integer> notes) {
        if (state != IdleState.INSTANCE) {
            throw new IllegalStateException("Refill only while IDLE, ATM is " + state.name());
        }
        dispenser.load(notes);
    }

    public synchronized String getStateName() {
        return state.name();
    }

    public synchronized List<Card> getRetainedCards() {
        return List.copyOf(retainedCards);
    }

    public long getCashAvailable() {
        return dispenser.totalCash();
    }

    public Map<Denomination, Integer> getInventory() {
        return dispenser.inventory();
    }

    public String getId() {
        return id;
    }

    // ------------------------------------------------ hooks for the states
    // Package-private and only ever called by a state, i.e. while this
    // Atm's lock is already held.

    BankService bank() {
        return bank;
    }

    CashDispenser dispenser() {
        return dispenser;
    }

    Card card() {
        return card;
    }

    AuthToken token() {
        return token;
    }

    void moveTo(AtmState next) {
        state = next;
    }

    void acceptCard(Card inserted) {
        card = inserted;
    }

    void startSession(AuthToken issued) {
        token = issued;
    }

    /** Normal end of a session: log out at the bank, hand the card back. */
    void returnCard() {
        endSession();
    }

    /** Card blocked: log out, but keep the card inside the machine. */
    void captureCard() {
        retainedCards.add(card);
        endSession();
    }

    Transaction record(TransactionType type, long amount, Map<Denomination, Integer> notes, long balanceAfter) {
        String txnId = id + "-" + (++transactionSequence);
        return new Transaction(txnId, type, card.masked(), amount, notes, balanceAfter, Instant.now());
    }

    private void endSession() {
        bank.endSession(token);
        token = null;
        card = null;
        state = IdleState.INSTANCE;
    }
}
