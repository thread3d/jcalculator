package dev.jcalculator.harness;

/** Signals malformed JSON input or a value the codec cannot represent. */
public final class JsonException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message) {
        super(message);
    }

    /**
     * Creates a refusal that keeps the underlying conversion failure as its cause.
     *
     * @param message what the caller must change
     * @param cause   the conversion failure, for example a rounding or overflow error
     */
    public JsonException(String message, Throwable cause) {
        super(message, cause);
    }
}
