package lld.atm;

/**
 * Notes the machine can hold. Declared highest value first, so values()
 * is already the order the dispensing chain needs.
 */
public enum Denomination {
    RS_500(500),
    RS_200(200),
    RS_100(100);

    private final int value;

    Denomination(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }

    /** Every withdrawal or deposit amount must be a multiple of this. */
    public static int smallest() {
        Denomination[] all = values();
        return all[all.length - 1].value;
    }
}
