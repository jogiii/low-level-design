package lld.atm;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stand-in for the bank's back end. Many ATMs (threads) call it
 * at the same time, so every piece of shared state is either a concurrent
 * map or guarded by the lock of the object it lives in.
 */
public class Bank implements BankService {
    public static final int MAX_PIN_ATTEMPTS = 3;

    private final Map<String, Account> accounts = new ConcurrentHashMap<>();
    private final Map<String, CardRecord> cards = new ConcurrentHashMap<>();
    // token value -> account number. Removing a token is atomic, so a
    // logged-out session can never be used again.
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    public Bank() {
        this(Clock.systemDefaultZone());
    }

    /** The clock is injectable so tests can control "today" and card expiry. */
    public Bank(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // ---------------------------------------------------------------- setup

    public Account openAccount(String accountNumber, long openingBalance, long dailyWithdrawalLimit) {
        Account account = new Account(accountNumber, openingBalance, dailyWithdrawalLimit);
        if (accounts.putIfAbsent(accountNumber, account) != null) {
            throw new IllegalArgumentException("Account already exists: " + accountNumber);
        }
        return account;
    }

    /** Issues a card for an existing account. Two cards may share one account (joint holders). */
    public Card issueCard(String cardNumber, String accountNumber, YearMonth expiry, String pin) {
        if (!accounts.containsKey(accountNumber)) {
            throw new IllegalArgumentException("No such account: " + accountNumber);
        }
        validatePinFormat(pin);
        Card card = new Card(cardNumber, accountNumber, expiry);
        if (cards.putIfAbsent(cardNumber, new CardRecord(card, hash(cardNumber, pin))) != null) {
            throw new IllegalArgumentException("Card already issued: " + cardNumber);
        }
        return card;
    }

    // ---------------------------------------------------------- BankService

    @Override
    public void verifyCard(Card card) {
        CardRecord record = recordFor(card);
        if (record.isBlocked()) {
            throw new AtmException(AtmException.Reason.CARD_BLOCKED, card + " is blocked");
        }
        if (YearMonth.now(clock).isAfter(card.expiry())) {
            throw new AtmException(AtmException.Reason.CARD_EXPIRED, card + " expired " + card.expiry());
        }
    }

    @Override
    public AuthToken authenticate(Card card, String pin) {
        CardRecord record = recordFor(card);
        record.checkPin(hash(card.cardNumber(), pin == null ? "" : pin)); // throws on failure
        AuthToken token = new AuthToken(UUID.randomUUID().toString());
        sessions.put(token.value(), card.accountNumber());
        return token;
    }

    @Override
    public long getBalance(AuthToken token) {
        return accountFor(token).getBalance();
    }

    @Override
    public long withdraw(AuthToken token, long amount) {
        requirePositive(amount);
        return accountFor(token).withdraw(amount, LocalDate.now(clock));
    }

    @Override
    public long deposit(AuthToken token, long amount) {
        requirePositive(amount);
        return accountFor(token).deposit(amount);
    }

    @Override
    public void endSession(AuthToken token) {
        if (token != null) {
            sessions.remove(token.value());
        }
    }

    // -------------------------------------------------------------- helpers

    private CardRecord recordFor(Card card) {
        CardRecord record = card == null ? null : cards.get(card.cardNumber());
        // Also compare the account number, so a forged card that copies a real
        // card number but points at another account is rejected.
        if (record == null || !record.card.equals(card)) {
            throw new AtmException(AtmException.Reason.CARD_INVALID, "Card not recognised");
        }
        return record;
    }

    private Account accountFor(AuthToken token) {
        String accountNumber = token == null ? null : sessions.get(token.value());
        if (accountNumber == null) {
            throw new AtmException(AtmException.Reason.SESSION_INVALID, "Session expired or invalid");
        }
        return accounts.get(accountNumber);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw new AtmException(AtmException.Reason.INVALID_AMOUNT, "Amount must be positive: " + amount);
        }
    }

    private static void validatePinFormat(String pin) {
        if (pin == null || !pin.matches("\\d{4}")) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits");
        }
    }

    /** Only a salted hash of the PIN is stored; the bank never keeps the PIN itself. */
    private static byte[] hash(String cardNumber, String pin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((cardNumber + ":" + pin).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    /**
     * Bank-side data about one card. Its lock makes "compare PIN, bump the
     * failure counter, maybe block" one atomic step, so two ATMs guessing
     * at the same time cannot get more than MAX_PIN_ATTEMPTS tries between them.
     */
    private static final class CardRecord {
        private final Card card;
        private final byte[] pinHash;

        // Guarded by "this".
        private int failedAttempts;
        private boolean blocked;

        CardRecord(Card card, byte[] pinHash) {
            this.card = card;
            this.pinHash = pinHash;
        }

        synchronized void checkPin(byte[] candidateHash) {
            if (blocked) {
                throw new AtmException(AtmException.Reason.CARD_BLOCKED, card + " is blocked");
            }
            // isEqual runs in constant time, so response timing leaks nothing about the PIN.
            if (MessageDigest.isEqual(pinHash, candidateHash)) {
                failedAttempts = 0;
                return;
            }
            failedAttempts++;
            if (failedAttempts >= MAX_PIN_ATTEMPTS) {
                blocked = true;
                throw new AtmException(AtmException.Reason.CARD_BLOCKED,
                        "Wrong PIN " + MAX_PIN_ATTEMPTS + " times; " + card + " is now blocked");
            }
            throw new AtmException(AtmException.Reason.INVALID_PIN,
                    "Wrong PIN; " + (MAX_PIN_ATTEMPTS - failedAttempts) + " attempt(s) left");
        }

        synchronized boolean isBlocked() {
            return blocked;
        }
    }
}
