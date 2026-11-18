package atm;

/**
 * A bank account linked to a {@link Card}.
 *
 * THREAD SAFETY: several ATM threads can operate on the same account at the
 * same time, so every method that reads or writes the mutable state
 * ({@code balance}, {@code withdrawnToday}) is declared {@code synchronized}.
 * Without it, two concurrent withdrawals could both pass the balance check
 * and overdraw the account.
 */
public class Account {

    /** Default per-day withdrawal ceiling when the caller does not give one. */
    public static final double DEFAULT_DAILY_LIMIT = 10000.0;

    private final String holder;
    private final double dailyLimit;
    private double balance;
    private double withdrawnToday;

    /** Full constructor: holder name, opening balance and daily limit. */
    public Account(String holder, double openingBalance, double dailyLimit) {
        if (holder == null || holder.trim().length() == 0) {
            throw new IllegalArgumentException("account holder must not be blank");
        }
        if (openingBalance < 0) {
            throw new IllegalArgumentException("opening balance must not be negative");
        }
        if (dailyLimit <= 0) {
            throw new IllegalArgumentException("daily limit must be greater than zero");
        }
        this.holder = holder.trim();
        this.balance = openingBalance;
        this.dailyLimit = dailyLimit;
        this.withdrawnToday = 0.0;
    }

    /** Overloaded constructor using {@link #DEFAULT_DAILY_LIMIT}. */
    public Account(String holder, double openingBalance) {
        this(holder, openingBalance, DEFAULT_DAILY_LIMIT);
    }

    /* ------------------------------------------------------------------ *
     *                          TRANSACTIONS                               *
     * ------------------------------------------------------------------ */

    /**
     * Debits the account after checking the amount, the available balance and
     * the remaining daily limit.
     *
     * @return the balance after the debit
     * @throws InsufficientBalanceException   if the account cannot cover it
     * @throws DailyLimitExceededException    if it would breach the day's ceiling
     */
    public synchronized double withdraw(double amount)
            throws InsufficientBalanceException, DailyLimitExceededException {
        requirePositive(amount);

        if (amount > balance) {
            throw new InsufficientBalanceException(amount, balance, holder);
        }
        if (withdrawnToday + amount > dailyLimit) {
            throw new DailyLimitExceededException(amount, dailyLimit, withdrawnToday, holder);
        }

        balance = balance - amount;
        withdrawnToday = withdrawnToday + amount;
        return balance;
    }

    /**
     * Undoes a withdrawal that was debited but never actually dispensed
     * (for example because the ATM's cash vault ran dry). Both the balance
     * and the day's withdrawal counter are restored, so the customer is not
     * charged for cash they never received and does not lose daily limit.
     */
    public synchronized void refundWithdrawal(double amount) {
        requirePositive(amount);
        balance = balance + amount;
        withdrawnToday = Math.max(0.0, withdrawnToday - amount);
    }

    /** Credits the account. */
    public synchronized double deposit(double amount) {
        requirePositive(amount);
        balance = balance + amount;
        return balance;
    }

    /** Starts a new banking day: clears the withdrawal counter. */
    public synchronized void resetDailyCounter() {
        withdrawnToday = 0.0;
    }

    private static void requirePositive(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
    }

    /* ------------------------------------------------------------------ *
     *                             ACCESSORS                               *
     * ------------------------------------------------------------------ */

    public String getHolder() {
        return holder;
    }

    public double getDailyLimit() {
        return dailyLimit;
    }

    public synchronized double getBalance() {
        return balance;
    }

    public synchronized double getWithdrawnToday() {
        return withdrawnToday;
    }

    public synchronized double getRemainingDailyLimit() {
        return dailyLimit - withdrawnToday;
    }

    @Override
    public synchronized String toString() {
        return "Account[" + holder + ", balance=Rs." + String.format("%.2f", Double.valueOf(balance))
                + ", withdrawnToday=Rs." + String.format("%.2f", Double.valueOf(withdrawnToday))
                + ", dailyLimit=Rs." + String.format("%.2f", Double.valueOf(dailyLimit)) + "]";
    }
}
