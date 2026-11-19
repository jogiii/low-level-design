package lld.atm;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The machine's cash unit. It builds the chain 500 -> 200 -> 100 and splits
 * every withdrawal into two steps:
 *
 *   plan()     - "can I pay this exact amount?" (nothing moves)
 *   dispense() - actually remove the planned notes
 *
 * The ATM debits the bank account between the two steps, so a customer is
 * never charged for cash the machine cannot give, and the machine never
 * hands out cash the bank refused to debit.
 *
 * Mutators are package-private: only Atm (under its own lock) moves notes.
 */
public class CashDispenser {
    private final NoteDispenser head;
    private final Map<Denomination, NoteDispenser> byDenomination = new EnumMap<>(Denomination.class);

    public CashDispenser(Map<Denomination, Integer> initialNotes) {
        // Build from the smallest note up, so each link knows its successor.
        NoteDispenser next = null;
        Denomination[] highestFirst = Denomination.values();
        for (int i = highestFirst.length - 1; i >= 0; i--) {
            Denomination d = highestFirst[i];
            next = new NoteDispenser(d, initialNotes.getOrDefault(d, 0), next);
            byDenomination.put(d, next);
        }
        this.head = next;
    }

    /** @return the notes that make up exactly {@code amount}, or empty if impossible */
    synchronized Optional<Map<Denomination, Integer>> plan(long amount) {
        if (amount <= 0) {
            return Optional.empty();
        }
        Map<Denomination, Integer> notes = new EnumMap<>(Denomination.class);
        long uncovered = head.plan(amount, notes);
        return uncovered == 0 ? Optional.of(Collections.unmodifiableMap(notes)) : Optional.empty();
    }

    /** Removes a plan's notes. All-or-nothing: checks every cassette before touching any. */
    synchronized void dispense(Map<Denomination, Integer> notes) {
        notes.forEach((d, n) -> {
            if (byDenomination.get(d).count() < n) {
                throw new IllegalStateException("Plan is stale: not enough " + d);
            }
        });
        notes.forEach((d, n) -> byDenomination.get(d).remove(n));
    }

    /** Puts notes in: a customer deposit or an operator refill. */
    synchronized void load(Map<Denomination, Integer> notes) {
        notes.forEach((d, n) -> {
            if (n < 0) {
                throw new IllegalArgumentException("Negative note count for " + d);
            }
        });
        notes.forEach((d, n) -> byDenomination.get(d).add(n));
    }

    public synchronized long totalCash() {
        long total = 0;
        for (Map.Entry<Denomination, NoteDispenser> e : byDenomination.entrySet()) {
            total += (long) e.getKey().value() * e.getValue().count();
        }
        return total;
    }

    /** Snapshot: denomination -> notes left, highest first. */
    public synchronized Map<Denomination, Integer> inventory() {
        Map<Denomination, Integer> snapshot = new EnumMap<>(Denomination.class);
        byDenomination.forEach((d, slot) -> snapshot.put(d, slot.count()));
        return Collections.unmodifiableMap(snapshot);
    }

    /** Value of a set of notes, e.g. {500 x 2, 100 x 3} -> 1300. */
    static long valueOf(Map<Denomination, Integer> notes) {
        long total = 0;
        for (Map.Entry<Denomination, Integer> e : notes.entrySet()) {
            total += (long) e.getKey().value() * e.getValue();
        }
        return total;
    }
}
