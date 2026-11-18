package atm;

/**
 * The cash vault inside the ATMs.
 *
 * SPEC POINT - "Each ATM is a thread sharing a synchronized cash vault".
 *
 * Exactly ONE CashVault object is created in {@link ATMSystem} and handed to
 * every ATM thread. All mutating methods are {@code synchronized} on the
 * vault instance, which makes "check the cash, then take the cash" a single
 * atomic step. Without that lock two ATMs could both see Rs.5000 available
 * and both dispense it, driving the vault negative.
 */
public class CashVault {

    private final double openingCash;
    private double cashAvailable;
    private double totalDispensed;
    private int dispenseCount;

    public CashVault(double openingCash) {
        if (openingCash < 0) {
            throw new IllegalArgumentException("opening cash must not be negative");
        }
        this.openingCash = openingCash;
        this.cashAvailable = openingCash;
        this.totalDispensed = 0.0;
        this.dispenseCount = 0;
    }

    /**
     * Atomically reserves and removes cash for one customer.
     *
     * @param atmId  which ATM is asking (used in the exception message)
     * @param amount how much cash to dispense
     * @return the cash left in the vault after dispensing
     * @throws InsufficientCashException if the vault cannot cover the amount
     */
    public synchronized double dispense(String atmId, double amount)
            throws InsufficientCashException {
        if (amount <= 0) {
            throw new IllegalArgumentException("dispense amount must be greater than zero");
        }
        if (amount > cashAvailable) {
            throw new InsufficientCashException(atmId, amount, cashAvailable);
        }
        cashAvailable = cashAvailable - amount;
        totalDispensed = totalDispensed + amount;
        dispenseCount = dispenseCount + 1;
        return cashAvailable;
    }

    /** Tops the vault up, e.g. after a cash-replenishment visit. */
    public synchronized void refill(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("refill amount must be greater than zero");
        }
        cashAvailable = cashAvailable + amount;
    }

    /* ------------------------------------------------------------------ *
     *                             ACCESSORS                               *
     * ------------------------------------------------------------------ */

    public synchronized double getCashAvailable() {
        return cashAvailable;
    }

    public synchronized double getTotalDispensed() {
        return totalDispensed;
    }

    public synchronized int getDispenseCount() {
        return dispenseCount;
    }

    public double getOpeningCash() {
        return openingCash;
    }

    @Override
    public synchronized String toString() {
        return "CashVault[available=Rs." + String.format("%.2f", Double.valueOf(cashAvailable))
                + ", dispensed=Rs." + String.format("%.2f", Double.valueOf(totalDispensed))
                + ", transactions=" + dispenseCount + "]";
    }
}
