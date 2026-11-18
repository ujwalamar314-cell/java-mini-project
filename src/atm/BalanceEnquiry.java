package atm;

/**
 * SPEC POINT - "BalanceEnquiry override execute()".
 *
 * The second concrete subclass of {@link ATMOperation}. It overrides
 * {@code execute()} with a much narrower contract than the abstract method
 * declares: a balance enquiry can only fail on a bad PIN, so it never throws
 * {@code DailyLimitExceededException}, {@code InsufficientBalanceException}
 * or {@code InsufficientCashException}. Narrowing the {@code throws} clause in
 * an override is legal in Java, and it also touches no cash - which is why it
 * needs no {@link CashVault}.
 */
public class BalanceEnquiry extends ATMOperation {

    public BalanceEnquiry(String atmId, Card card, String enteredPin, Authenticator authenticator) {
        super(atmId, card, enteredPin, authenticator);
    }

    @Override
    public String getName() {
        return "BalanceEnquiry";
    }

    @Override
    public String execute() throws InvalidPinException {
        authenticate();

        Account account = card.getAccount();
        return String.format(
                "%s for %s (card %s) | available Rs.%.2f | daily limit left Rs.%.2f",
                new Object[] {
                        getName(),
                        account.getHolder(),
                        card.maskedCardNumber(),
                        Double.valueOf(account.getBalance()),
                        Double.valueOf(account.getRemainingDailyLimit())
                });
    }
}
