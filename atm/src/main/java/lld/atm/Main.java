package lld.atm;

import java.time.YearMonth;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Main {

    public static void main(String[] args) throws Exception {
        Bank bank = new Bank();
        YearMonth expiry = YearMonth.now().plusYears(3);

        bank.openAccount("ACC-1", 20_000, 10_000);
        Card asha = bank.issueCard("4111111111111111", "ACC-1", expiry, "1234");

        Atm atm = new Atm("ATM-01", bank, new CashDispenser(Map.of(
                Denomination.RS_500, 20,
                Denomination.RS_200, 20,
                Denomination.RS_100, 20)));

        section("1. Happy path: balance, withdraw, deposit, eject");
        atm.insertCard(asha);
        System.out.println("State after card: " + atm.getStateName());
        atm.enterPin("1234");
        System.out.println("State after PIN:  " + atm.getStateName());
        print(atm.checkBalance());
        print(atm.withdraw(2_800)); // 5 x 500 + 1 x 200 + 1 x 100
        print(atm.deposit(Map.of(Denomination.RS_500, 2, Denomination.RS_100, 3)));
        System.out.println("Cash left in ATM: " + atm.getCashAvailable() + " " + atm.getInventory());

        section("2. Business failures keep the session open");
        attempt("Withdraw 150 (not a multiple of 100)", () -> atm.withdraw(150));
        attempt("Withdraw 50,000 (more than the ATM holds)", () -> atm.withdraw(50_000));
        attempt("Withdraw 9,000 (daily limit 10,000, 2,800 used)", () -> atm.withdraw(9_000));
        System.out.println("Still " + atm.getStateName() + "; one more try:");
        print(atm.withdraw(1_000));
        atm.ejectCard();
        System.out.println("State after eject: " + atm.getStateName());

        section("3. Actions in the wrong state are rejected");
        attempt("Withdraw with no card", () -> atm.withdraw(500));
        attempt("PIN with no card", () -> atm.enterPin("1234"));

        section("4. Three wrong PINs block the card");
        bank.openAccount("ACC-2", 5_000, 10_000);
        Card ravi = bank.issueCard("5500000000000004", "ACC-2", expiry, "4321");
        atm.insertCard(ravi);
        attempt("PIN 0000", () -> atm.enterPin("0000"));
        attempt("PIN 1111", () -> atm.enterPin("1111"));
        attempt("PIN 2222", () -> atm.enterPin("2222"));
        System.out.println("State: " + atm.getStateName() + ", cards retained: " + atm.getRetainedCards());
        attempt("Insert the blocked card again", () -> atm.insertCard(ravi));

        section("5. Two ATMs race on one joint account (balance 10,000, each asks 6,000)");
        raceOnJointAccount(bank, expiry);
    }

    /**
     * Two holders of a joint account withdraw at the same instant from two
     * different machines. Account.withdraw does check-then-debit under the
     * account's lock, so exactly one of them succeeds and the balance can
     * never go negative.
     */
    private static void raceOnJointAccount(Bank bank, YearMonth expiry) throws Exception {
        bank.openAccount("JOINT-1", 10_000, 50_000);
        Card husband = bank.issueCard("4000000000000002", "JOINT-1", expiry, "1111");
        Card wife = bank.issueCard("4000000000000010", "JOINT-1", expiry, "2222");

        Atm atmA = new Atm("ATM-A", bank, new CashDispenser(Map.of(Denomination.RS_500, 50)));
        Atm atmB = new Atm("ATM-B", bank, new CashDispenser(Map.of(Denomination.RS_500, 50)));
        atmA.insertCard(husband);
        atmA.enterPin("1111");
        atmB.insertCard(wife);
        atmB.enterPin("2222");

        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<String> a = pool.submit(() -> withdrawWhenReleased(atmA, go));
            Future<String> b = pool.submit(() -> withdrawWhenReleased(atmB, go));
            go.countDown(); // release both threads together
            System.out.println("  " + a.get());
            System.out.println("  " + b.get());
        }
        print(atmA.checkBalance());
        System.out.println("Cash left: ATM-A " + atmA.getCashAvailable() + ", ATM-B " + atmB.getCashAvailable()
                + "  (only the winner's machine paid out)");
        atmA.ejectCard();
        atmB.ejectCard();
    }

    private static String withdrawWhenReleased(Atm atm, CountDownLatch go) throws InterruptedException {
        go.await();
        try {
            Transaction t = atm.withdraw(6_000);
            return atm.getId() + " dispensed " + t.amount() + ", balance now " + t.balanceAfter();
        } catch (AtmException e) {
            return atm.getId() + " refused: " + e.reason();
        }
    }

    // ------------------------------------------------------------ printing

    private static void section(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }

    private static void print(Transaction t) {
        String notes = t.notes().isEmpty() ? "" : "  notes=" + new java.util.TreeMap<>(t.notes());
        System.out.printf("  [%s] %-15s amount=%-6d balance=%-6d%s%n",
                t.id(), t.type(), t.amount(), t.balanceAfter(), notes);
    }

    private static void attempt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + " -> OK");
        } catch (AtmException e) {
            System.out.println("  " + label + " -> " + e.reason() + ": " + e.getMessage());
        } catch (IllegalStateException e) {
            System.out.println("  " + label + " -> rejected: " + e.getMessage());
        }
    }
}
