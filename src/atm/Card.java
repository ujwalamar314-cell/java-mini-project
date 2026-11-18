package atm;

/**
 * A customer's ATM card.
 *
 * SPEC POINT - "Class: Card (card no., PIN, account) with constructor
 * overloading".
 *
 * The three required attributes are {@code cardNumber}, {@code pin} and
 * {@code account}. Four constructors with DIFFERENT parameter lists are
 * declared; constructors 2, 3 and 4 all delegate to the canonical
 * constructor 1 through {@code this(...)}, which is the standard way to
 * overload constructors without duplicating validation logic.
 */
public class Card {

    private final String cardNumber;
    private final String pin;
    private final Account account;

    /* ------------------------------------------------------------------ *
     *                    CONSTRUCTOR OVERLOADING                          *
     * ------------------------------------------------------------------ */

    /**
     * Constructor 1 (canonical) - card number, PIN and account all supplied.
     *
     * @param cardNumber card number, e.g. "4000123456781234"
     * @param pin        exactly four digits, e.g. "1234"
     * @param account    the bank account this card is linked to
     */
    public Card(String cardNumber, String pin, Account account) {
        if (cardNumber == null || cardNumber.trim().length() == 0) {
            throw new IllegalArgumentException("card number must not be blank");
        }
        if (pin == null || !pin.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("PIN must be exactly 4 digits, e.g. 1234");
        }
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        this.cardNumber = cardNumber.trim();
        this.pin = pin;
        this.account = account;
    }

    /**
     * Constructor 2 - no account supplied, so a brand new zero-balance
     * account is opened for the card holder.
     */
    public Card(String cardNumber, String pin) {
        this(cardNumber, pin,
                new Account(autoHolderName(cardNumber), 0.0, Account.DEFAULT_DAILY_LIMIT));
    }

    /**
     * Constructor 3 - numeric overload. Accepts a {@code long} card number
     * and an {@code int} PIN and formats them into the canonical String form.
     */
    public Card(long cardNumber, int pin, Account account) {
        this(formatCardNumber(cardNumber), formatPin(pin), account);
    }

    /** Constructor 4 - no-argument demo card. */
    public Card() {
        this("0000000000000000", "0000",
                new Account("Guest", 0.0, Account.DEFAULT_DAILY_LIMIT));
    }

    /* ------------------------------------------------------------------ *
     *                              HELPERS                                *
     * ------------------------------------------------------------------ */

    /**
     * Builds a short placeholder holder name for a card opened without an
     * account, e.g. "Card holder ****2222". Kept short so summary tables stay
     * aligned, and it never contains the full card number.
     */
    private static String autoHolderName(String cardNumber) {
        String digits = cardNumber == null ? "" : cardNumber.replaceAll("[^0-9]", "");
        String tail = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "Card holder ****" + tail;
    }

    /** Pads a numeric card number to 16 digits. */
    private static String formatCardNumber(long cardNumber) {
        if (cardNumber < 0) {
            throw new IllegalArgumentException("card number must not be negative");
        }
        return String.format("%016d", Long.valueOf(cardNumber));
    }

    /** Pads a numeric PIN to 4 digits. */
    private static String formatPin(int pin) {
        if (pin < 0 || pin > 9999) {
            throw new IllegalArgumentException("PIN must be between 0 and 9999");
        }
        return String.format("%04d", Integer.valueOf(pin));
    }

    /* ------------------------------------------------------------------ *
     *                             ACCESSORS                               *
     * ------------------------------------------------------------------ */

    public String getCardNumber() {
        return cardNumber;
    }

    public String getPin() {
        return pin;
    }

    public Account getAccount() {
        return account;
    }

    /**
     * Returns the card number with all but the last four digits hidden,
     * so receipts and logs never leak the full number.
     */
    public String maskedCardNumber() {
        String digits = cardNumber.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "XXXXXXXXXXXX";
        }
        return "XXXX-XXXX-XXXX-" + digits.substring(digits.length() - 4);
    }

    @Override
    public String toString() {
        return "Card[" + maskedCardNumber() + ", holder=" + account.getHolder()
                + ", balance=Rs." + String.format("%.2f", Double.valueOf(account.getBalance())) + "]";
    }
}
