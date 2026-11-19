package lld.atm;

import java.time.Instant;
import java.util.Map;

/**
 * Immutable receipt for one completed operation. Failed operations never
 * produce a Transaction; they throw AtmException instead.
 *
 * @param notes notes dispensed (withdrawal) or accepted (deposit); empty for enquiries
 */
public record Transaction(
        String id,
        TransactionType type,
        String maskedCard,
        long amount,
        Map<Denomination, Integer> notes,
        long balanceAfter,
        Instant timestamp) {

    public Transaction {
        notes = Map.copyOf(notes); // defensive copy keeps the record truly immutable
    }
}
