package atm;

/**
 * SPEC POINT - "Inheritance: Abstract ATMOperation; Withdrawal and
 * BalanceEnquiry override execute()".
 *
 * {@code ATMOperation} is declared {@code abstract} and cannot be
 * instantiated. It holds the state common to every kind of transaction
 * (which ATM, which card, which PIN was typed, who checks the PIN) and
 * implements the step they all share - authentication.
 *
 * The abstract method {@link #execute()} is the polymorphic entry point: the
 * {@link ATM} thread calls {@code operation.execute()} on an
 * {@code ATMOperation} reference, and at run time Java dispatches to the
 * overriding version in {@link Withdrawal} or {@link BalanceEnquiry}.
 */
public abstract class ATMOperation {

    protected final String atmId;
    protected final Card card;
    protected final String enteredPin;
    protected final Authenticator authenticator;

    protected ATMOperation(String atmId, Card card, String enteredPin, Authenticator authenticator) {
        if (atmId == null || atmId.trim().length() == 0) {
            throw new IllegalArgumentException("atmId must not be blank");
        }
        if (card == null) {
            throw new IllegalArgumentException("card must not be null");
        }
        if (authenticator == null) {
            throw new IllegalArgumentException("authenticator must not be null");
        }
        this.atmId = atmId;
        this.card = card;
        this.enteredPin = enteredPin;
        this.authenticator = authenticator;
    }

    /**
     * Performs the transaction and returns a printable receipt.
     *
     * Every subclass must supply its own body - that is what makes this class
     * abstract.
     *
     * @throws InvalidPinException           if the PIN does not match
     * @throws DailyLimitExceededException   if the day's ceiling is breached
     * @throws InsufficientBalanceException  if the account cannot cover it
     * @throws InsufficientCashException     if the vault cannot cover it
     */
    public abstract String execute()
            throws InvalidPinException,
                   DailyLimitExceededException,
                   InsufficientBalanceException,
                   InsufficientCashException;

    /** Human-readable name of the operation, e.g. "Withdrawal". */
    public abstract String getName();

    /**
     * The one step shared by all operations: ask the {@link Authenticator} to
     * check the PIN and raise the project's custom exception when it is wrong.
     */
    protected void authenticate() throws InvalidPinException {
        if (!authenticator.verifyPin(card, enteredPin)) {
            throw new InvalidPinException(card.maskedCardNumber(), card.getAccount().getHolder());
        }
    }

    public String getAtmId() {
        return atmId;
    }

    public Card getCard() {
        return card;
    }

    public String getEnteredPin() {
        return enteredPin;
    }

    @Override
    public String toString() {
        return getName() + "[" + card.maskedCardNumber() + " @" + atmId + "]";
    }
}
