package lld.atm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AtmTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneOffset.UTC);
    private static final YearMonth EXPIRY = YearMonth.of(2029, 12);

    private Bank bank;
    private Card card;
    private Atm atm;

    @BeforeEach
    void setUp() {
        bank = new Bank(CLOCK);
        bank.openAccount("ACC-1", 10_000, 20_000);
        card = bank.issueCard("4111111111111111", "ACC-1", EXPIRY, "1234");
        atm = new Atm("ATM-T", bank, new CashDispenser(Map.of(
                Denomination.RS_500, 10, Denomination.RS_200, 10, Denomination.RS_100, 10)));
    }

    private void login() {
        atm.insertCard(card);
        atm.enterPin("1234");
    }

    private static AtmException.Reason reasonOf(Runnable action) {
        return assertThrows(AtmException.class, action::run).reason();
    }

    @Test
    void withdrawalUsesLargestNotesFirstAndDebitsAccount() {
        login();
        Transaction t = atm.withdraw(1_800);

        assertEquals(Map.of(Denomination.RS_500, 3, Denomination.RS_200, 1, Denomination.RS_100, 1), t.notes());
        assertEquals(8_200, t.balanceAfter());
        assertEquals(8_000 - 1_800, atm.getCashAvailable());
    }

    @Test
    void cashUnavailableLeavesAccountUntouched() {
        login();
        assertEquals(AtmException.Reason.CASH_UNAVAILABLE, reasonOf(() -> atm.withdraw(9_000)));
        assertEquals(10_000, atm.checkBalance().balanceAfter());
    }

    @Test
    void insufficientFundsLeavesCashUntouched() {
        bank.openAccount("ACC-POOR", 300, 20_000);
        Card poor = bank.issueCard("4222222222222222", "ACC-POOR", EXPIRY, "9999");
        atm.insertCard(poor);
        atm.enterPin("9999");

        assertEquals(AtmException.Reason.INSUFFICIENT_FUNDS, reasonOf(() -> atm.withdraw(500)));
        assertEquals(8_000, atm.getCashAvailable());
        assertEquals("AUTHENTICATED", atm.getStateName()); // session survives a failed transaction
    }

    @Test
    void dailyLimitIsEnforcedAcrossTransactions() {
        bank.openAccount("ACC-RICH", 100_000, 2_000);
        Card rich = bank.issueCard("4333333333333333", "ACC-RICH", EXPIRY, "5555");
        atm.insertCard(rich);
        atm.enterPin("5555");

        atm.withdraw(1_500);
        assertEquals(AtmException.Reason.DAILY_LIMIT_EXCEEDED, reasonOf(() -> atm.withdraw(600)));
        atm.withdraw(500); // exactly reaches the limit
    }

    @Test
    void invalidAmountsAreRejected() {
        login();
        assertEquals(AtmException.Reason.INVALID_AMOUNT, reasonOf(() -> atm.withdraw(150)));
        assertEquals(AtmException.Reason.INVALID_AMOUNT, reasonOf(() -> atm.withdraw(0)));
        assertEquals(AtmException.Reason.INVALID_AMOUNT, reasonOf(() -> atm.deposit(Map.of())));
    }

    @Test
    void depositCreditsAccountAndAddsNotes() {
        login();
        Transaction t = atm.deposit(Map.of(Denomination.RS_500, 4));
        assertEquals(12_000, t.balanceAfter());
        assertEquals(14, atm.getInventory().get(Denomination.RS_500));
    }

    @Test
    void threeWrongPinsBlockAndRetainTheCard() {
        atm.insertCard(card);
        assertEquals(AtmException.Reason.INVALID_PIN, reasonOf(() -> atm.enterPin("0000")));
        assertEquals("CARD_INSERTED", atm.getStateName());
        assertEquals(AtmException.Reason.INVALID_PIN, reasonOf(() -> atm.enterPin("0000")));
        assertEquals(AtmException.Reason.CARD_BLOCKED, reasonOf(() -> atm.enterPin("0000")));

        assertEquals("IDLE", atm.getStateName());
        assertEquals(List.of(card), atm.getRetainedCards());
        assertEquals(AtmException.Reason.CARD_BLOCKED, reasonOf(() -> atm.insertCard(card)));
    }

    @Test
    void correctPinResetsTheFailureCounter() {
        atm.insertCard(card);
        reasonOf(() -> atm.enterPin("0000"));
        reasonOf(() -> atm.enterPin("0000"));
        atm.enterPin("1234");
        atm.ejectCard();

        atm.insertCard(card);
        assertEquals(AtmException.Reason.INVALID_PIN, reasonOf(() -> atm.enterPin("0000")));
    }

    @Test
    void expiredAndUnknownCardsAreRejectedAtInsert() {
        bank.openAccount("ACC-OLD", 1_000, 1_000);
        Card old = bank.issueCard("4444444444444444", "ACC-OLD", YearMonth.of(2026, 5), "1234");
        Card forged = new Card("4111111111111111", "ACC-OLD", EXPIRY); // real number, wrong account

        assertEquals(AtmException.Reason.CARD_EXPIRED, reasonOf(() -> atm.insertCard(old)));
        assertEquals(AtmException.Reason.CARD_INVALID, reasonOf(() -> atm.insertCard(forged)));
        assertEquals("IDLE", atm.getStateName());
    }

    @Test
    void actionsInTheWrongStateThrowIllegalState() {
        assertThrows(IllegalStateException.class, () -> atm.withdraw(500));
        assertThrows(IllegalStateException.class, () -> atm.ejectCard());
        atm.insertCard(card);
        assertThrows(IllegalStateException.class, () -> atm.checkBalance());
        assertThrows(IllegalStateException.class, () -> atm.insertCard(card));
        assertThrows(IllegalStateException.class, () -> atm.refill(Map.of(Denomination.RS_100, 1)));
    }

    @Test
    void tokenIsUselessAfterSessionEnds() {
        AuthToken token = bank.authenticate(card, "1234");
        bank.endSession(token);
        assertEquals(AtmException.Reason.SESSION_INVALID, reasonOf(() -> bank.getBalance(token)));
    }

    @Test
    void concurrentWithdrawalsOnOneAccountNeverOverdraw() throws Exception {
        bank.openAccount("JOINT", 10_000, 1_000_000);
        int machines = 20; // each asks for 1,000, so only 10 can succeed
        List<Atm> atms = new ArrayList<>();
        for (int i = 0; i < machines; i++) {
            Card c = bank.issueCard("5000" + String.format("%012d", i), "JOINT", EXPIRY, "1234");
            Atm a = new Atm("ATM-" + i, bank, new CashDispenser(Map.of(Denomination.RS_500, 10)));
            a.insertCard(c);
            a.enterPin("1234");
            atms.add(a);
        }

        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(machines)) {
            for (Atm a : atms) {
                results.add(pool.submit(() -> {
                    go.await();
                    try {
                        a.withdraw(1_000);
                        return true;
                    } catch (AtmException e) {
                        return false;
                    }
                }));
            }
            go.countDown();
        }

        int succeeded = 0;
        long cashPaidOut = 0;
        for (int i = 0; i < machines; i++) {
            if (results.get(i).get()) {
                succeeded++;
            }
            cashPaidOut += 5_000 - atms.get(i).getCashAvailable();
        }
        assertEquals(10, succeeded);
        assertEquals(10_000, cashPaidOut); // every rupee debited was dispensed, and no more
        assertEquals(0, atms.get(0).checkBalance().balanceAfter());
        assertTrue(atms.stream().allMatch(a -> a.getStateName().equals("AUTHENTICATED")));
    }
}
