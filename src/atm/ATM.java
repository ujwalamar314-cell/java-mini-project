package atm;

import java.util.ArrayList;
import java.util.List;

/**
 * SPEC POINT - "Thread: Each ATM is a thread sharing a synchronized cash
 * vault; use join()."
 *
 * {@code ATM} extends {@link Thread}, so every ATM in the system really is a
 * separate thread of execution. All of them are constructed with the SAME
 * {@link CashVault} and the SAME {@link Authenticator}, which is why those two
 * classes are synchronized internally.
 *
 * {@link #run()} drains this ATM's queue of customer operations. Each one is
 * invoked through an {@link ATMOperation} reference, so the same loop handles
 * withdrawals and balance enquiries polymorphically, and each custom exception
 * is caught separately and reported as a blocked transaction.
 */
public class ATM extends Thread {

    private final String atmId;
    private final CashVault vault;
    private final Authenticator authenticator;
    private final TransactionLog log;
    private final List<ATMOperation> pendingOperations;

    private int servedCount;
    private int blockedCount;

    public ATM(String atmId, CashVault vault, Authenticator authenticator, TransactionLog log) {
        super(atmId);                       // the thread's name is the ATM id
        if (atmId == null || atmId.trim().length() == 0) {
            throw new IllegalArgumentException("atmId must not be blank");
        }
        if (vault == null || authenticator == null || log == null) {
            throw new IllegalArgumentException("vault, authenticator and log are all required");
        }
        this.atmId = atmId;
        this.vault = vault;
        this.authenticator = authenticator;
        this.log = log;
        this.pendingOperations = new ArrayList<ATMOperation>();
        this.servedCount = 0;
        this.blockedCount = 0;
    }

    /* ------------------------------------------------------------------ *
     *                     BUILDING THE CUSTOMER QUEUE                     *
     * ------------------------------------------------------------------ */

    /** Queues any operation, useful for showing polymorphism directly. */
    public void addOperation(ATMOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        pendingOperations.add(operation);
    }

    /** Queues a withdrawal against the shared vault. */
    public void addWithdrawal(Card card, String enteredPin, double amount) {
        addOperation(new Withdrawal(atmId, card, enteredPin, authenticator, amount, vault));
    }

    /** Queues a balance enquiry. */
    public void addBalanceEnquiry(Card card, String enteredPin) {
        addOperation(new BalanceEnquiry(atmId, card, enteredPin, authenticator));
    }

    public int getPendingCount() {
        return pendingOperations.size();
    }

    /* ------------------------------------------------------------------ *
     *                          THE THREAD BODY                            *
     * ------------------------------------------------------------------ */

    @Override
    public void run() {
        log.note(atmId, "thread started, " + pendingOperations.size()
                + " customer(s) waiting, vault holds Rs."
                + String.format("%.2f", Double.valueOf(vault.getCashAvailable())));

        for (ATMOperation operation : pendingOperations) {
            try {
                String receipt = operation.execute();
                servedCount = servedCount + 1;
                log.approved(atmId, receipt);

            } catch (InvalidPinException e) {
                blockedCount = blockedCount + 1;
                log.blocked(atmId, "InvalidPinException -> " + e.getMessage());

            } catch (DailyLimitExceededException e) {
                blockedCount = blockedCount + 1;
                log.blocked(atmId, "DailyLimitExceededException -> " + e.getMessage());

            } catch (InsufficientBalanceException e) {
                blockedCount = blockedCount + 1;
                log.blocked(atmId, "InsufficientBalanceException -> " + e.getMessage());

            } catch (InsufficientCashException e) {
                blockedCount = blockedCount + 1;
                log.blocked(atmId, "InsufficientCashException -> " + e.getMessage()
                        + " (debit was rolled back)");

            } catch (RuntimeException e) {
                blockedCount = blockedCount + 1;
                log.blocked(atmId, e.getClass().getSimpleName() + " -> " + e.getMessage());
            }
        }

        log.note(atmId, "thread finished: " + servedCount + " approved, "
                + blockedCount + " blocked");
    }

    /* ------------------------------------------------------------------ *
     *                             ACCESSORS                               *
     * ------------------------------------------------------------------ */

    public String getAtmId() {
        return atmId;
    }

    public CashVault getVault() {
        return vault;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public int getServedCount() {
        return servedCount;
    }

    public int getBlockedCount() {
        return blockedCount;
    }

    @Override
    public String toString() {
        return "ATM[" + atmId + ", served=" + servedCount + ", blocked=" + blockedCount + "]";
    }
}
