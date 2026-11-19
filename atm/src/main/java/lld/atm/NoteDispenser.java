package lld.atm;

import java.util.Map;

/**
 * One link in the Chain of Responsibility: the cassette for a single
 * denomination. It pays as much of the amount as it can with its own notes
 * and passes the remainder to the next (smaller) denomination.
 *
 * Not thread-safe on its own; CashDispenser's lock guards the whole chain.
 */
final class NoteDispenser {
    private final Denomination denomination;
    private final NoteDispenser next;
    private int count;

    NoteDispenser(Denomination denomination, int count, NoteDispenser next) {
        if (count < 0) {
            throw new IllegalArgumentException(denomination + " count must be >= 0, was " + count);
        }
        this.denomination = denomination;
        this.count = count;
        this.next = next;
    }

    /**
     * Works out notes for {@code amount} without removing any.
     *
     * @param out collects denomination -> note count
     * @return the part of the amount the chain could not cover (0 means success)
     */
    long plan(long amount, Map<Denomination, Integer> out) {
        int used = (int) Math.min(amount / denomination.value(), count);
        if (used > 0) {
            out.put(denomination, used);
        }
        long remaining = amount - (long) used * denomination.value();
        if (remaining == 0 || next == null) {
            return remaining;
        }
        return next.plan(remaining, out);
    }

    int count() {
        return count;
    }

    void remove(int notes) {
        if (notes > count) {
            throw new IllegalStateException("Only " + count + " x " + denomination + " left, asked " + notes);
        }
        count -= notes;
    }

    void add(int notes) {
        count += notes;
    }
}
