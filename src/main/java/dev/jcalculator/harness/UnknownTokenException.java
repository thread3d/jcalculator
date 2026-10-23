package dev.jcalculator.harness;

/** Signals a token that is not one of the calculator's buttons. */
public final class UnknownTokenException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    private final String token;

    public UnknownTokenException(String token) {
        super("unknown token: " + token);
        this.token = token;
    }

    public String token() {
        return token;
    }
}
