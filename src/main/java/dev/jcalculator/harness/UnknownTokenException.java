package dev.jcalculator.harness;

/** Signals a token that is not one of the calculator's buttons. */
public final class UnknownTokenException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String token;

    public UnknownTokenException(String token) {
        this(token, "unknown token: " + token);
    }

    /**
     * Creates a refusal whose message explains why the token was not a button.
     *
     * @param token   the refused token, which {@link #token()} returns unchanged
     * @param message a caller-facing explanation; {@link Tokens} adds the page and, for a
     *                hexadecimal letter, which page that button belongs to
     */
    public UnknownTokenException(String token, String message) {
        super(message);
        this.token = token;
    }

    public String token() {
        return token;
    }
}
