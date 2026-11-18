package atm;

/**
 * SPEC POINT - "Withdrawal ... override execute()".
 *
 * A concrete subclass of {@link ATMOperation}. It reuses the inherited
 * {@link #authenticate()} step and then adds the two things only a withdrawal
 * needs: debiting the customer's {@link Account} and taking physical cash out
 * of the shared {@link CashVault}.
 */
public class Withdrawal extends ATMOperation {

    private final double amount;
    private final CashVault vault;

    public Withdrawal(String atmId, Card card, String enteredPin,
                      Authenticator authenticator, double amount, CashVault vault) {
        super(atmId, card, enteredPin, authenticator);
        if (amount <= 0) {
            throw new IllegalArgumentException("withdrawal amount must be greater than zero");
        }
        if (vault == null) {
            throw new IllegalArgumentException("cash vault must not be null");
        }
        this.amount = amount;
        this.vault = vault;
    }

    @Override
    public String getName() {
        return "Withdrawal";
    }

    public double getAmount() {
        return amount;
    }

    /**
     * Order of work matters here:
     *
     * 1. authenticate()            - wrong PIN is blocked before anything else
     * 2. account.withdraw(amount)  - may throw DailyLimitExceededException or
     *                                InsufficientBalanceException
     * 3. vault.dispense(amount)    - may throw InsufficientCashException
     *
     * If step 3 fails after step 2 succeeded, the debit is rolled back with
     * {@link Account#refundWithdrawal(double)} so the customer is never charged
     * for cash the machine could not hand over.
     */
    @Override
    public String execute()
            throws InvalidPinException,
                   DailyLimitExceededException,
                   InsufficientBalanceException,
                   InsufficientCashException {

        authenticate();

        Account account = card.getAccount();
        account.withdraw(amount);

        try {
            vault.dispense(atmId, amount);
        } catch (InsufficientCashException vaultEmpty) {
            account.refundWithdrawal(amount);
            throw vaultEmpty;
        }

        return String.format(
                "%s of Rs.%.2f to %s (card %s) | balance now Rs.%.2f | withdrawn today Rs.%.2f of Rs.%.2f limit",
                new Object[] {
                        getName(),
                        Double.valueOf(amount),
                        account.getHolder(),
                        card.maskedCardNumber(),
                        Double.valueOf(account.getBalance()),
                        Double.valueOf(account.getWithdrawnToday()),
                        Double.valueOf(account.getDailyLimit())
                });
    }
}
