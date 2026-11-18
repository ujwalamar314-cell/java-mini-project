package atm;

import java.util.ArrayList;
import java.util.List;

/**
 * A thread-safe running log of everything the ATMs do.
 *
 * Concurrent threads writing straight to {@code System.out} can interleave
 * their output mid-line. Every method here is {@code synchronized}, so each
 * transaction is printed as one unbroken line and the counters stay exact -
 * which is what lets the summary printed after {@code join()} be trusted.
 */
public class TransactionLog {

    private final List<String> entries;
    private int approvedCount;
    private int blockedCount;

    public TransactionLog() {
        this.entries = new ArrayList<String>();
        this.approvedCount = 0;
        this.blockedCount = 0;
    }

    /** Records a transaction that went through. */
    public synchronized void approved(String atmId, String receipt) {
        approvedCount = approvedCount + 1;
        write(atmId, "APPROVED", receipt);
    }

    /** Records a transaction the ATM refused. */
    public synchronized void blocked(String atmId, String reason) {
        blockedCount = blockedCount + 1;
        write(atmId, "BLOCKED ", reason);
    }

    /** Records an informational line from a thread. */
    public synchronized void note(String atmId, String message) {
        write(atmId, "NOTE    ", message);
    }

    private void write(String atmId, String tag, String message) {
        String line = "[" + atmId + "] " + tag + "  " + message;
        entries.add(line);
        System.out.println(line);
    }

    public synchronized List<String> getEntries() {
        return new ArrayList<String>(entries);
    }

    public synchronized int getApprovedCount() {
        return approvedCount;
    }

    public synchronized int getBlockedCount() {
        return blockedCount;
    }

    public synchronized int getTotalCount() {
        return approvedCount + blockedCount;
    }
}
