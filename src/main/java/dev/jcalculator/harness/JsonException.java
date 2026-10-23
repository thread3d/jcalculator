package dev.jcalculator.harness;

/** Signals malformed JSON input or a value the codec cannot represent. */
public final class JsonException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public JsonException(String message) {
        super(message);
    }
}
