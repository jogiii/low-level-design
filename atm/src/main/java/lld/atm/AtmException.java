package lld.atm;

/**
 * A business failure the customer should see on screen ("insufficient funds").
 * Programming errors (calling withdraw before a PIN) stay IllegalStateException.
 */
public class AtmException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Reason {
        CARD_INVALID,
        CARD_EXPIRED,
        CARD_BLOCKED,
        INVALID_PIN,
        INVALID_AMOUNT,
        INSUFFICIENT_FUNDS,
        DAILY_LIMIT_EXCEEDED,
        CASH_UNAVAILABLE,
        SESSION_INVALID
    }

    private final Reason reason;

    public AtmException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
