package lld.atm;

/**
 * Proof of a successful PIN check, issued by the bank. The ATM sends this,
 * not an account number, with every money operation, so the machine can
 * only touch the account whose PIN was actually entered.
 */
public record AuthToken(String value) {
    @Override
    public String toString() {
        return "AuthToken[***]";
    }
}
