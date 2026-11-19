package lld.atm;

/**
 * Everything the ATM needs from the bank's back end. The ATM depends on
 * this interface, not on Bank, so a real network client could replace the
 * in-memory Bank without touching any ATM code (Dependency Inversion).
 */
public interface BankService {

    /**
     * Checks the card is known, not blocked and not expired.
     *
     * @throws AtmException CARD_INVALID, CARD_BLOCKED or CARD_EXPIRED
     */
    void verifyCard(Card card);

    /**
     * Checks the PIN. Wrong PINs are counted per card; reaching the limit
     * blocks the card.
     *
     * @throws AtmException INVALID_PIN or CARD_BLOCKED
     */
    AuthToken authenticate(Card card, String pin);

    long getBalance(AuthToken token);

    /**
     * Debits the account.
     *
     * @return balance after the debit
     * @throws AtmException INSUFFICIENT_FUNDS or DAILY_LIMIT_EXCEEDED; the balance is unchanged
     */
    long withdraw(AuthToken token, long amount);

    /** @return balance after the credit */
    long deposit(AuthToken token, long amount);

    /** Invalidates the token. Safe to call more than once. */
    void endSession(AuthToken token);
}
