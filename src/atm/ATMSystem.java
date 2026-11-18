package atm;

import java.util.ArrayList;
import java.util.List;

/**
 * Driver class - run this one.
 *
 * It builds a small bank (accounts + cards), ONE shared cash vault, ONE shared
 * authenticator and THREE ATM threads, loads each ATM with a mix of valid and
 * invalid customer requests, starts them all, waits for them with
 * {@code join()}, and then prints a summary that can only be trusted because
 * every thread has finished.
 */
public class ATMSystem {

    /** Opening cash in the shared vault. */
    private static final double VAULT_CASH = 12000.0;

    public static void main(String[] args) throws InterruptedException {

        /* ---------------- shared, thread-safe resources ---------------- */

        CashVault vault = new CashVault(VAULT_CASH);
        Authenticator authenticator = new PinAuthenticator();
        TransactionLog log = new TransactionLog();

        /* ---------------- accounts and cards ----------------
         * Each card is built with a DIFFERENT constructor, to exercise the
         * constructor overloading declared in Card.
         */

        Account ashaAccount = new Account("Asha Rao", 25000.0, 10000.0);
        Card ashaCard = new Card("4000123456781234", "1234", ashaAccount);        // ctor 1

        Account vikramAccount = new Account("Vikram Shetty", 8000.0, 5000.0);
        Card vikramCard = new Card(4111222233334444L, 4321, vikramAccount);        // ctor 3 (numeric)

        Account meeraAccount = new Account("Meera Nair", 3000.0, 10000.0);
        Card meeraCard = new Card("4222987654321098", "9999", meeraAccount);       // ctor 1

        Account kiranAccount = new Account("Kiran Kumar", 50000.0, 40000.0);
        Card kiranCard = new Card("4444555566667777", "2468", kiranAccount);       // ctor 1

        Card freshCard = new Card("4333000011112222", "0000");                     // ctor 2 (opens a zero-balance account)
        Card guestCard = new Card();                                               // ctor 4 (no arguments)

        List<Account> accounts = new ArrayList<Account>();
        accounts.add(ashaAccount);
        accounts.add(vikramAccount);
        accounts.add(meeraAccount);
        accounts.add(kiranAccount);
        accounts.add(freshCard.getAccount());
        accounts.add(guestCard.getAccount());

        printHeader(vault, authenticator);

        /* ---------------- three ATMs, three threads ---------------- */

        ATM atm1 = new ATM("ATM-1", vault, authenticator, log);
        ATM atm2 = new ATM("ATM-2", vault, authenticator, log);
        ATM atm3 = new ATM("ATM-3", vault, authenticator, log);

        // ATM-1: one clean withdrawal, then the same customer over the daily limit.
        atm1.addWithdrawal(ashaCard, "1234", 5000.0);      // approved
        atm1.addWithdrawal(ashaCard, "1234", 6000.0);      // blocked: daily limit (Rs.5000 left)
        atm1.addBalanceEnquiry(ashaCard, "1234");          // approved

        // ATM-2: valid withdrawal, over-limit retry, and an account too small to cover it.
        atm2.addWithdrawal(vikramCard, "4321", 4000.0);    // approved
        atm2.addWithdrawal(vikramCard, "4321", 3000.0);    // blocked: daily limit (Rs.1000 left)
        atm2.addWithdrawal(meeraCard, "9999", 5000.0);     // blocked: insufficient balance
        atm2.addBalanceEnquiry(meeraCard, "9999");         // approved

        // ATM-3: a big draw on the shared vault, a wrong PIN, and a brand new empty account.
        atm3.addWithdrawal(kiranCard, "2468", 9000.0);     // competes for the shared vault
        atm3.addWithdrawal(guestCard, "1111", 500.0);      // blocked: invalid PIN
        atm3.addWithdrawal(freshCard, "0000", 500.0);      // blocked: insufficient balance
        atm3.addBalanceEnquiry(kiranCard, "2468");         // approved

        List<ATM> atms = new ArrayList<ATM>();
        atms.add(atm1);
        atms.add(atm2);
        atms.add(atm3);

        /* ---------------- start them all, then join them all ---------------- */

        System.out.println();
        System.out.println("--- " + atms.size() + " ATM threads starting; all share one synchronized vault ---");
        for (ATM atm : atms) {
            atm.start();                    // each ATM now runs on its own thread
        }
        for (ATM atm : atms) {
            atm.join();                     // main thread waits for this ATM to finish
        }
        System.out.println("--- all ATM threads have finished (join returned) ---");

        printSummary(atms, vault, authenticator, log, accounts);
    }

    private static void printHeader(CashVault vault, Authenticator authenticator) {
        System.out.println("==============================================================");
        System.out.println("        MULTI-THREADED ATM SYSTEM  (package atm)");
        System.out.println("==============================================================");
        System.out.println("Shared cash vault   : Rs." + money(vault.getCashAvailable()));
        System.out.println("Shared authenticator: " + authenticator.getClass().getName());
        System.out.println("Every mutating method of CashVault, Account,");
        System.out.println("PinAuthenticator and TransactionLog is synchronized.");
        System.out.println("==============================================================");
    }

    private static void printSummary(List<ATM> atms, CashVault vault,
                                     Authenticator authenticator, TransactionLog log,
                                     List<Account> accounts) {
        System.out.println();
        System.out.println("========================= SUMMARY ==========================");

        System.out.println("Per-ATM thread results:");
        int totalServed = 0;
        int totalBlocked = 0;
        for (ATM atm : atms) {
            System.out.println(String.format("  %-7s thread=%-7s state=%-12s approved=%d blocked=%d",
                    new Object[] {
                            atm.getAtmId(),
                            atm.getName(),
                            atmStateName(atm),
                            Integer.valueOf(atm.getServedCount()),
                            Integer.valueOf(atm.getBlockedCount())
                    }));
            totalServed = totalServed + atm.getServedCount();
            totalBlocked = totalBlocked + atm.getBlockedCount();
        }

        System.out.println();
        System.out.println("Shared cash vault after all threads finished:");
        System.out.println("  opening cash    : Rs." + money(vault.getOpeningCash()));
        System.out.println("  cash dispensed  : Rs." + money(vault.getTotalDispensed())
                + "  (" + vault.getDispenseCount() + " dispense call(s))");
        System.out.println("  cash remaining  : Rs." + money(vault.getCashAvailable()));
        System.out.println("  vault invariant : dispensed + remaining == opening -> "
                + (Math.abs((vault.getTotalDispensed() + vault.getCashAvailable())
                        - vault.getOpeningCash()) < 0.005 ? "HOLDS" : "VIOLATED"));

        System.out.println();
        System.out.println("Accounts:");
        for (Account account : accounts) {
            System.out.println(String.format(
                    "  %-26s balance Rs.%12s   withdrawn today Rs.%10s   limit Rs.%10s",
                    new Object[] {
                            account.getHolder(),
                            money(account.getBalance()),
                            money(account.getWithdrawnToday()),
                            money(account.getDailyLimit())
                    }));
        }

        System.out.println();
        System.out.println("PIN verification attempts : " + attemptsOf(authenticator));
        System.out.println("Transactions approved     : " + log.getApprovedCount());
        System.out.println("Transactions blocked      : " + log.getBlockedCount());
        System.out.println("Total transactions        : " + log.getTotalCount()
                + "  (per-ATM counters agree: "
                + ((totalServed == log.getApprovedCount() && totalBlocked == log.getBlockedCount())
                        ? "yes" : "no") + ")");
        System.out.println("============================================================");
    }

    /** Reads the authenticator's counters when the concrete class exposes them. */
    private static String attemptsOf(Authenticator authenticator) {
        if (authenticator instanceof PinAuthenticator) {
            PinAuthenticator pin = (PinAuthenticator) authenticator;
            return pin.getTotalAttempts() + " (" + pin.getSuccessCount() + " ok, "
                    + pin.getFailureCount() + " rejected)";
        }
        return "not reported by " + authenticator.getClass().getName();
    }

    private static String atmStateName(ATM atm) {
        if (!atm.isAlive() && atm.getServedCount() + atm.getBlockedCount() > 0) {
            return "TERMINATED";
        }
        return atm.isAlive() ? "RUNNABLE" : "NEW";
    }

    private static String money(double amount) {
        return String.format("%,.2f", Double.valueOf(amount));
    }
}
