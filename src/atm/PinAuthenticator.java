package atm;

/**
 * The concrete {@link Authenticator} used by this project: a straight
 * comparison of the entered PIN against the PIN stored on the {@link Card}.
 *
 * One instance is shared by every ATM thread, so the two counters are updated
 * inside a {@code synchronized} method to keep them accurate under concurrency.
 */
public class PinAuthenticator implements Authenticator {

    private int successCount;
    private int failureCount;

    public PinAuthenticator() {
        this.successCount = 0;
        this.failureCount = 0;
    }

    @Override
    public synchronized boolean verifyPin(Card card, String enteredPin) {
        boolean matched = card != null && enteredPin != null && card.getPin().equals(enteredPin);
        if (matched) {
            successCount = successCount + 1;
        } else {
            failureCount = failureCount + 1;
        }
        return matched;
    }

    public synchronized int getSuccessCount() {
        return successCount;
    }

    public synchronized int getFailureCount() {
        return failureCount;
    }

    public synchronized int getTotalAttempts() {
        return successCount + failureCount;
    }

    @Override
    public String toString() {
        return "PinAuthenticator[ok=" + successCount + ", rejected=" + failureCount + "]";
    }
}
