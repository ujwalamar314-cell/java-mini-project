package atm;

/**
 * Raised by {@link CashVault#dispense(String, double)} when the shared vault
 * no longer holds enough notes for a withdrawal.
 *
 * This is the exception that only appears because the ATMs run concurrently
 * and share one vault: the customer's account may be perfectly healthy, yet
 * the machine simply has no cash left. {@link Withdrawal} catches it, refunds
 * the debit and rethrows it so the ATM can report a blocked transaction.
 */
public class InsufficientCashException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String atmId;
    private final double requestedAmount;
    private final double cashInVault;

    public InsufficientCashException(String atmId, double requestedAmount, double cashInVault) {
        super(String.format("%s vault out of cash - customer asked for Rs.%.2f, only Rs.%.2f left in the machine",
                new Object[] {
                        atmId,
                        Double.valueOf(requestedAmount),
                        Double.valueOf(cashInVault)
                }));
        this.atmId = atmId;
        this.requestedAmount = requestedAmount;
        this.cashInVault = cashInVault;
    }

    public String getAtmId() {
        return atmId;
    }

    public double getRequestedAmount() {
        return requestedAmount;
    }

    public double getCashInVault() {
        return cashInVault;
    }
}
