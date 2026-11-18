package atm;

/**
 * SPEC POINT - "Exception: Custom InvalidPinException".
 *
 * A checked exception (it extends {@link Exception}), so any code that calls
 * {@link ATMOperation#execute()} is forced by the compiler to handle a wrong
 * PIN. That is exactly the "block invalid operations" behaviour the project
 * requires: a bad PIN stops the transaction before the account or the cash
 * vault is touched.
 */
public class InvalidPinException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String maskedCardNumber;
    private final String holder;

    public InvalidPinException(String maskedCardNumber, String holder) {
        super("Invalid PIN for card " + maskedCardNumber
                + (holder == null ? "" : " (" + holder + ")")
                + " - transaction blocked, account untouched");
        this.maskedCardNumber = maskedCardNumber;
        this.holder = holder;
    }

    public InvalidPinException(String message) {
        super(message);
        this.maskedCardNumber = "unknown";
        this.holder = "unknown";
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public String getHolder() {
        return holder;
    }
}
