package atm;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free test harness (plain Java, no JUnit) so the project can be
 * verified with nothing but a JDK.
 *
 * Run it with:  java -ea -cp out atm.SelfTest
 * It prints PASS/FAIL per check and exits with status 1 if anything failed.
 *
 * The last two tests are the important ones: they hammer ONE shared vault and
 * ONE shared account from twelve threads at once and then assert exact totals.
 * If the {@code synchronized} keywords were removed from CashVault or Account,
 * those totals would drift and the tests would fail.
 */
public class SelfTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("==============================================================");
        System.out.println("           atm.SelfTest  -  plain Java test harness");
        System.out.println("==============================================================");

        testConstructorOverloading();
        testCardValidation();
        testPinAuthenticator();
        testInvalidPinBlocksAndLeavesStateUntouched();
        testDailyLimitBlocking();
        testInsufficientBalanceBlocking();
        testVaultEmptyRollsBackTheDebit();
        testInheritanceAndPolymorphism();
        testAtmIsAThreadAndJoinWaits();
        testConcurrentWithdrawalsKeepExactTotals();
        testConcurrentWithdrawalsOnAnEmptyingVault();

        System.out.println("--------------------------------------------------------------");
        System.out.println(String.format("RESULT: %d passed, %d failed", Integer.valueOf(passed),
                Integer.valueOf(failed)));
        System.out.println("==============================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    /* ------------------------------------------------------------------ *
     *                              TESTS                                  *
     * ------------------------------------------------------------------ */

    private static void testConstructorOverloading() {
        Account account = new Account("Overload Tester", 5000.0, 20000.0);

        Card c1 = new Card("4000123456781234", "1234", account);   // String, String, Account
        Card c2 = new Card("4333000011112222", "0000");             // String, String
        Card c3 = new Card(4111222233334444L, 4321, account);       // long, int, Account
        Card c4 = new Card();                                       // no arguments

        check(c1.getCardNumber().equals("4000123456781234"), "ctor 1 keeps the supplied card number");
        check(c1.getAccount() == account, "ctor 1 links the supplied account");

        check(c2.getCardNumber().equals("4333000011112222"), "ctor 2 keeps the supplied card number");
        check(c2.getAccount() != null, "ctor 2 opens an account automatically");
        check(c2.getAccount().getBalance() == 0.0, "ctor 2 account starts at zero balance");

        check(c3.getCardNumber().equals("4111222233334444"), "ctor 3 formats the long card number");
        check(c3.getPin().equals("4321"), "ctor 3 formats the int PIN to 4 digits");

        Card small = new Card(7L, 25, account);
        check(small.getCardNumber().equals("0000000000000007"), "ctor 3 left-pads a short card number");
        check(small.getPin().equals("0025"), "ctor 3 left-pads a short PIN");

        check(c4.getCardNumber().equals("0000000000000000"), "ctor 4 builds a guest card");
        check(c4.getPin().equals("0000"), "ctor 4 uses PIN 0000");

        check(c1.maskedCardNumber().equals("XXXX-XXXX-XXXX-1234"), "card number is masked in output");
        check(c1.toString().indexOf("4000123456781234") < 0, "toString never leaks the full card number");
    }

    private static void testCardValidation() {
        Account account = new Account("Validation Tester", 1000.0, 5000.0);

        check(cardRejected("4000123456781234", "12", account),
                "PIN shorter than 4 digits is rejected");
        check(cardRejected("4000123456781234", "abcd", account),
                "non-numeric PIN is rejected");
        check(cardRejected("   ", "1234", account),
                "blank card number is rejected");
        check(cardRejected("4000123456781234", "1234", null),
                "null account is rejected");
        check(accountRejected("", 100.0, 500.0),
                "blank account holder is rejected");
        check(accountRejected("Negative", -100.0, 500.0),
                "negative opening balance is rejected");
        check(accountRejected("Bad Limit", 100.0, 0.0),
                "zero daily limit is rejected");
    }

    private static void testPinAuthenticator() {
        Authenticator authenticator = new PinAuthenticator();
        Card card = new Card("4000123456781234", "1234", new Account("Pin Tester", 1000.0, 5000.0));

        check(authenticator.verifyPin(card, "1234"), "correct PIN verifies");
        check(!authenticator.verifyPin(card, "9999"), "wrong PIN does not verify");
        check(!authenticator.verifyPin(card, null), "null PIN does not verify");
        check(!authenticator.verifyPin(null, "1234"), "null card does not verify");

        PinAuthenticator concrete = (PinAuthenticator) authenticator;
        check(concrete.getSuccessCount() == 1, "authenticator counted 1 success");
        check(concrete.getFailureCount() == 3, "authenticator counted 3 failures");
        check(concrete.getTotalAttempts() == 4, "authenticator counted 4 attempts");
    }

    private static void testInvalidPinBlocksAndLeavesStateUntouched() throws Exception {
        CashVault vault = new CashVault(5000.0);
        Account account = new Account("Wrong Pin Customer", 10000.0, 10000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        ATMOperation operation = new Withdrawal("ATM-T", card, "0000", authenticator, 2000.0, vault);

        boolean threw = false;
        try {
            operation.execute();
        } catch (InvalidPinException expected) {
            threw = true;
            check(expected.getMessage().indexOf("Invalid PIN") >= 0,
                    "InvalidPinException carries a useful message");
        }
        check(threw, "wrong PIN raises InvalidPinException");
        check(close(account.getBalance(), 10000.0), "wrong PIN left the balance untouched");
        check(close(account.getWithdrawnToday(), 0.0), "wrong PIN left the daily counter untouched");
        check(close(vault.getCashAvailable(), 5000.0), "wrong PIN left the vault untouched");
    }

    private static void testDailyLimitBlocking() throws Exception {
        CashVault vault = new CashVault(50000.0);
        Account account = new Account("Limit Tester", 40000.0, 10000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        new Withdrawal("ATM-T", card, "1234", authenticator, 6000.0, vault).execute();
        check(close(account.getBalance(), 34000.0), "first withdrawal debited the account");
        check(close(account.getWithdrawnToday(), 6000.0), "first withdrawal counted against the limit");
        check(close(account.getRemainingDailyLimit(), 4000.0), "remaining limit is 4000");

        boolean threw = false;
        try {
            new Withdrawal("ATM-T", card, "1234", authenticator, 6000.0, vault).execute();
        } catch (DailyLimitExceededException expected) {
            threw = true;
            check(close(expected.getRemainingLimit(), 4000.0),
                    "DailyLimitExceededException knows the remaining limit");
            check(close(expected.getAlreadyWithdrawn(), 6000.0),
                    "DailyLimitExceededException knows what was already withdrawn");
        }
        check(threw, "breaching the daily limit raises DailyLimitExceededException");
        check(close(account.getBalance(), 34000.0), "blocked withdrawal did not debit the account");
        check(close(vault.getTotalDispensed(), 6000.0), "blocked withdrawal took no cash");

        // A withdrawal that fits inside the remaining limit still works.
        new Withdrawal("ATM-T", card, "1234", authenticator, 4000.0, vault).execute();
        check(close(account.getBalance(), 30000.0), "withdrawal up to the exact remaining limit works");
        check(close(account.getRemainingDailyLimit(), 0.0), "limit is now fully used");

        account.resetDailyCounter();
        check(close(account.getRemainingDailyLimit(), 10000.0), "resetDailyCounter restores the limit");
    }

    private static void testInsufficientBalanceBlocking() throws Exception {
        CashVault vault = new CashVault(50000.0);
        Account account = new Account("Poor Tester", 500.0, 10000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        boolean threw = false;
        try {
            new Withdrawal("ATM-T", card, "1234", authenticator, 1000.0, vault).execute();
        } catch (InsufficientBalanceException expected) {
            threw = true;
            check(close(expected.getAvailableBalance(), 500.0),
                    "InsufficientBalanceException reports the real balance");
        }
        check(threw, "overdrawing raises InsufficientBalanceException");
        check(close(account.getBalance(), 500.0), "overdraft attempt left the balance untouched");
    }

    private static void testVaultEmptyRollsBackTheDebit() throws Exception {
        CashVault vault = new CashVault(100.0);
        Account account = new Account("Rollback Tester", 5000.0, 5000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        boolean threw = false;
        try {
            new Withdrawal("ATM-T", card, "1234", authenticator, 500.0, vault).execute();
        } catch (InsufficientCashException expected) {
            threw = true;
            check(expected.getAtmId().equals("ATM-T"), "InsufficientCashException names the ATM");
        }
        check(threw, "an empty vault raises InsufficientCashException");
        check(close(account.getBalance(), 5000.0), "debit was rolled back when the vault was empty");
        check(close(account.getWithdrawnToday(), 0.0), "daily counter was rolled back too");
        check(close(vault.getCashAvailable(), 100.0), "vault cash unchanged");
        check(vault.getDispenseCount() == 0, "no dispense was recorded");
    }

    private static void testInheritanceAndPolymorphism() throws Exception {
        CashVault vault = new CashVault(10000.0);
        Account account = new Account("Polymorphism Tester", 10000.0, 10000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        check(ATMOperation.class.isAssignableFrom(Withdrawal.class),
                "Withdrawal IS-A ATMOperation");
        check(ATMOperation.class.isAssignableFrom(BalanceEnquiry.class),
                "BalanceEnquiry IS-A ATMOperation");
        check(java.lang.reflect.Modifier.isAbstract(ATMOperation.class.getModifiers()),
                "ATMOperation is declared abstract");
        check(java.lang.reflect.Modifier.isAbstract(
                        ATMOperation.class.getMethod("execute").getModifiers()),
                "ATMOperation.execute() is declared abstract");
        check(Authenticator.class.isInterface(), "Authenticator is an interface");
        check(authenticator instanceof Authenticator,
                "PinAuthenticator implements Authenticator");

        // One array of the abstract type, two different concrete behaviours.
        ATMOperation[] operations = new ATMOperation[] {
                new Withdrawal("ATM-P", card, "1234", authenticator, 1500.0, vault),
                new BalanceEnquiry("ATM-P", card, "1234", authenticator)
        };

        check(operations[0].getName().equals("Withdrawal"), "getName() dispatches to Withdrawal");
        check(operations[1].getName().equals("BalanceEnquiry"), "getName() dispatches to BalanceEnquiry");

        String first = operations[0].execute();
        String second = operations[1].execute();
        check(first.indexOf("Withdrawal of Rs.1500.00") >= 0, "execute() ran the Withdrawal override");
        check(second.indexOf("BalanceEnquiry for Polymorphism Tester") >= 0,
                "execute() ran the BalanceEnquiry override");
        check(close(account.getBalance(), 8500.0), "polymorphic withdrawal debited correctly");
    }

    private static void testAtmIsAThreadAndJoinWaits() throws Exception {
        CashVault vault = new CashVault(5000.0);
        Account account = new Account("Thread Tester", 5000.0, 5000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();
        TransactionLog log = new TransactionLog();

        ATM atm = new ATM("ATM-JOIN", vault, authenticator, log);
        check(atm instanceof Thread, "ATM IS-A Thread");
        check(atm.getName().equals("ATM-JOIN"), "the thread is named after the ATM");

        atm.addWithdrawal(card, "1234", 1000.0);
        atm.addBalanceEnquiry(card, "1234");
        atm.addWithdrawal(card, "9999", 500.0);     // wrong PIN -> blocked
        check(atm.getPendingCount() == 3, "three operations were queued");

        atm.start();
        atm.join();                                  // main waits for the ATM thread

        check(!atm.isAlive(), "join() returned only after the thread terminated");
        check(atm.getServedCount() == 2, "ATM approved 2 transactions");
        check(atm.getBlockedCount() == 1, "ATM blocked 1 transaction");
        check(log.getApprovedCount() == 2, "shared log recorded 2 approvals");
        check(log.getBlockedCount() == 1, "shared log recorded 1 blocking");
        check(log.getTotalCount() == 3, "shared log recorded all 3 transactions");
        check(close(account.getBalance(), 4000.0), "balance reflects only the approved withdrawal");
        check(close(vault.getTotalDispensed(), 1000.0), "vault dispensed only the approved cash");
    }

    private static void testConcurrentWithdrawalsKeepExactTotals() throws Exception {
        final int threads = 12;
        final int operationsPerThread = 100;
        final double amount = 10.0;

        CashVault vault = new CashVault(20000.0);
        Account account = new Account("Concurrency Tester", 20000.0, 1000000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        WithdrawalWorker[] workers = startWorkers(threads, operationsPerThread, amount,
                card, vault, authenticator);

        double expectedCashOut = threads * operationsPerThread * amount;   // 12000.0
        int expectedOps = threads * operationsPerThread;                   // 1200

        check(sumOk(workers) == expectedOps,
                "all " + expectedOps + " concurrent withdrawals were approved");
        check(sumBlocked(workers) == 0, "nothing was blocked while cash and limit were available");
        check(close(vault.getTotalDispensed(), expectedCashOut),
                "vault dispensed exactly Rs." + money(expectedCashOut) + " (no lost updates)");
        check(close(vault.getCashAvailable(), 20000.0 - expectedCashOut),
                "vault remaining is exact");
        check(vault.getDispenseCount() == expectedOps,
                "vault counted exactly " + expectedOps + " dispense calls");
        check(close(account.getBalance(), 20000.0 - expectedCashOut),
                "account balance is exact after " + expectedOps + " concurrent debits");
        check(close(account.getWithdrawnToday(), expectedCashOut),
                "daily withdrawal counter is exact");
        check(vault.getCashAvailable() >= 0.0, "vault never went negative");

        PinAuthenticator pin = (PinAuthenticator) authenticator;
        check(pin.getTotalAttempts() == expectedOps,
                "shared authenticator counted every concurrent PIN check");
    }

    private static void testConcurrentWithdrawalsOnAnEmptyingVault() throws Exception {
        final int threads = 12;
        final int operationsPerThread = 100;
        final double amount = 10.0;

        // Demand is Rs.12000 but the vault only holds Rs.500: exactly 50 withdrawals
        // can be served and the other 1150 must be refused without corrupting state.
        CashVault vault = new CashVault(500.0);
        Account account = new Account("Drain Tester", 20000.0, 1000000.0);
        Card card = new Card("4000123456781234", "1234", account);
        Authenticator authenticator = new PinAuthenticator();

        WithdrawalWorker[] workers = startWorkers(threads, operationsPerThread, amount,
                card, vault, authenticator);

        int expectedServed = 50;
        check(sumOk(workers) == expectedServed,
                "exactly " + expectedServed + " withdrawals were served before the vault emptied");
        check(sumBlocked(workers) == (threads * operationsPerThread) - expectedServed,
                "every later withdrawal was blocked");
        check(close(vault.getTotalDispensed(), 500.0), "vault dispensed exactly its Rs.500.00");
        check(close(vault.getCashAvailable(), 0.0), "vault ended at zero, never negative");
        check(vault.getDispenseCount() == expectedServed, "dispense count matches served count");
        check(close(account.getBalance(), 20000.0 - 500.0),
                "1150 rolled-back debits left the balance correct");
        check(close(account.getWithdrawnToday(), 500.0),
                "rolled-back debits did not consume the daily limit");
    }

    /* ------------------------------------------------------------------ *
     *                        CONCURRENCY HELPERS                          *
     * ------------------------------------------------------------------ */

    /** A thread that fires many withdrawals at the shared card, vault and authenticator. */
    private static class WithdrawalWorker extends Thread {

        private final Card card;
        private final CashVault vault;
        private final Authenticator authenticator;
        private final int operations;
        private final double amount;

        final AtomicInteger ok = new AtomicInteger(0);
        final AtomicInteger blocked = new AtomicInteger(0);

        WithdrawalWorker(String name, Card card, CashVault vault, Authenticator authenticator,
                         int operations, double amount) {
            super(name);
            this.card = card;
            this.vault = vault;
            this.authenticator = authenticator;
            this.operations = operations;
            this.amount = amount;
        }

        @Override
        public void run() {
            for (int i = 0; i < operations; i++) {
                ATMOperation operation =
                        new Withdrawal(getName(), card, card.getPin(), authenticator, amount, vault);
                try {
                    operation.execute();
                    ok.incrementAndGet();
                } catch (Exception refused) {
                    blocked.incrementAndGet();
                }
            }
        }
    }

    private static WithdrawalWorker[] startWorkers(int threads, int operationsPerThread,
                                                   double amount, Card card, CashVault vault,
                                                   Authenticator authenticator)
            throws InterruptedException {
        WithdrawalWorker[] workers = new WithdrawalWorker[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new WithdrawalWorker("Worker-" + (i + 1), card, vault, authenticator,
                    operationsPerThread, amount);
        }
        for (int i = 0; i < threads; i++) {
            workers[i].start();
        }
        for (int i = 0; i < threads; i++) {
            workers[i].join();          // wait for every worker before asserting
        }
        return workers;
    }

    private static int sumOk(WithdrawalWorker[] workers) {
        int total = 0;
        for (int i = 0; i < workers.length; i++) {
            total = total + workers[i].ok.get();
        }
        return total;
    }

    private static int sumBlocked(WithdrawalWorker[] workers) {
        int total = 0;
        for (int i = 0; i < workers.length; i++) {
            total = total + workers[i].blocked.get();
        }
        return total;
    }

    /* ------------------------------------------------------------------ *
     *                        ASSERTION HELPERS                            *
     * ------------------------------------------------------------------ */

    private static void check(boolean condition, String description) {
        if (condition) {
            passed = passed + 1;
            System.out.println("  PASS  " + description);
        } else {
            failed = failed + 1;
            System.out.println("  FAIL  " + description);
        }
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.005;
    }

    private static String money(double amount) {
        return String.format("%.2f", Double.valueOf(amount));
    }

    /**
     * True when building a Card with these arguments throws
     * IllegalArgumentException. The constructor call has to sit INSIDE the try
     * block - passing an already-constructed Card would throw too early.
     */
    private static boolean cardRejected(String cardNumber, String pin, Account account) {
        try {
            new Card(cardNumber, pin, account);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    /** True when building an Account with these arguments is rejected. */
    private static boolean accountRejected(String holder, double openingBalance, double dailyLimit) {
        try {
            new Account(holder, openingBalance, dailyLimit);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }
}
