package atm;

/**
 * SPEC POINT - "Interface/Package: Authenticator interface (verifyPin) in
 * package atm".
 *
 * This interface lives in the {@code atm} package (every source file in this
 * project starts with {@code package atm;}) and declares a single abstract
 * method, {@link #verifyPin(Card, String)}. Because it is an interface, the
 * rest of the program depends only on this contract: {@link ATMOperation}
 * holds an {@code Authenticator} reference and never mentions the concrete
 * class, so the PIN-checking strategy can be swapped (chip-and-PIN service,
 * biometrics, a bank's remote API) without touching any other file.
 */
public interface Authenticator {

    /**
     * Checks the PIN typed by the customer against the PIN stored on the card.
     *
     * @param card        the card that was inserted
     * @param enteredPin  the PIN typed on the keypad
     * @return {@code true} when the PIN matches, {@code false} otherwise
     */
    boolean verifyPin(Card card, String enteredPin);
}
