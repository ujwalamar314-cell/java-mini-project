package atm;

/**
 * SPEC POINT - "Exception: Custom DailyLimitExceededException".
 *
 * A checked exception raised by {@link Account#withdraw(double)} when a
 * withdrawal would push the day's total past the account's ceiling. It carries
 * the numbers needed to explain the refusal to the customer.
 */
public class DailyLimitExceededException extends Exception {

    private static final long serialVersionUID = 1L;

    private final double attemptedAmount;
    private final double dailyLimit;
    private final double alreadyWithdrawn;

    public DailyLimitExceededException(double attemptedAmount, double dailyLimit,
                                       double alreadyWithdrawn, String holder) {
        super(String.format(
                "Daily limit exceeded for %s - tried Rs.%.2f, limit Rs.%.2f, already withdrawn Rs.%.2f, only Rs.%.2f left today",
                new Object[] {
                        holder,
                        Double.valueOf(attemptedAmount),
                        Double.valueOf(dailyLimit),
                        Double.valueOf(alreadyWithdrawn),
                        Double.valueOf(dailyLimit - alreadyWithdrawn)
                }));
        this.attemptedAmount = attemptedAmount;
        this.dailyLimit = dailyLimit;
        this.alreadyWithdrawn = alreadyWithdrawn;
    }

    public double getAttemptedAmount() {
        return attemptedAmount;
    }

    public double getDailyLimit() {
        return dailyLimit;
    }

    public double getAlreadyWithdrawn() {
        return alreadyWithdrawn;
    }

    public double getRemainingLimit() {
        return dailyLimit - alreadyWithdrawn;
    }
}
