package lld.atm;

import java.time.YearMonth;
import java.util.Objects;

/**
 * What the customer physically inserts. It carries no PIN and no balance:
 * the bank owns both, the card is only an identifier.
 */
public record Card(String cardNumber, String accountNumber, YearMonth expiry) {
    public Card {
        Objects.requireNonNull(cardNumber, "cardNumber");
        Objects.requireNonNull(accountNumber, "accountNumber");
        Objects.requireNonNull(expiry, "expiry");
    }

    /** Never print a full card number: "XXXX-XXXX-XXXX-1234". */
    public String masked() {
        String last4 = cardNumber.substring(Math.max(0, cardNumber.length() - 4));
        return "XXXX-XXXX-XXXX-" + last4;
    }

    @Override
    public String toString() {
        return "Card[" + masked() + "]";
    }
}
