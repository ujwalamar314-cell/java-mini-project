package atm;

/**
 * Raised when the customer's account cannot cover the withdrawal.
 *
 * A third custom checked exception, used alongside the two required by the
 * spec so that every kind of refused transaction has its own precise type
 * instead of one vague error.
 */
public class InsufficientBalanceException extends Exception {

    private static final long serialVersionUID = 1L;

    private final double requestedAmount;
    private final double availableBalance;

    public InsufficientBalanceException(double requestedAmount, double availableBalance, String holder) {
        super(String.format("Insufficient balance for %s - requested Rs.%.2f, available Rs.%.2f",
                new Object[] {
                        holder,
                        Double.valueOf(requestedAmount),
                        Double.valueOf(availableBalance)
                }));
        this.requestedAmount = requestedAmount;
        this.availableBalance = availableBalance;
    }

    public double getRequestedAmount() {
        return requestedAmount;
    }

    public double getAvailableBalance() {
        return availableBalance;
    }
}
