package lld.atm;

import java.time.LocalDate;

/**
 * A bank account. The account is the unit of locking: two ATMs debiting the
 * same account serialise on it, two ATMs on different accounts never contend.
 *
 * Mutators are package-private so only Bank can move money.
 */
public class Account {
    private final String accountNumber;
    private final long dailyWithdrawalLimit;

    // All guarded by "this".
    private long balance;
    private LocalDate limitDay;
    private long withdrawnOnLimitDay;

    public Account(String accountNumber, long openingBalance, long dailyWithdrawalLimit) {
        if (openingBalance < 0 || dailyWithdrawalLimit <= 0) {
            throw new IllegalArgumentException("balance must be >= 0 and limit > 0");
        }
        this.accountNumber = accountNumber;
        this.balance = openingBalance;
        this.dailyWithdrawalLimit = dailyWithdrawalLimit;
    }

    /**
     * Check-then-debit under one lock, so two ATMs racing on this account can
     * never both pass the balance check and overdraw it.
     */
    synchronized long withdraw(long amount, LocalDate today) {
        if (!today.equals(limitDay)) {
            limitDay = today; // new day: the daily limit resets
            withdrawnOnLimitDay = 0;
        }
        if (amount > balance) {
            throw new AtmException(AtmException.Reason.INSUFFICIENT_FUNDS,
                    "Insufficient funds: balance " + balance + ", requested " + amount);
        }
        if (withdrawnOnLimitDay + amount > dailyWithdrawalLimit) {
            throw new AtmException(AtmException.Reason.DAILY_LIMIT_EXCEEDED,
                    "Daily limit " + dailyWithdrawalLimit + " exceeded; already withdrawn "
                            + withdrawnOnLimitDay + " today");
        }
        balance -= amount;
        withdrawnOnLimitDay += amount;
        return balance;
    }

    synchronized long deposit(long amount) {
        balance += amount;
        return balance;
    }

    public synchronized long getBalance() {
        return balance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }
}
