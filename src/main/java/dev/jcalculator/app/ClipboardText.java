package dev.jcalculator.app;

import dev.jcalculator.engine.CalculatorMode;
import dev.jcalculator.harness.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * What may be pasted, and what it becomes.
 *
 * <p>The original's {@code CopyPasteManager} keeps a legal character set per page — the basic set for
 * the standard page, plus the operators the scientific page has, plus the hexadecimal letters on the
 * programmer page — and refuses a paste that contains anything else instead of pressing the buttons
 * it happens to recognise. This class applies the same rule and turns an accepted text into the
 * button tokens that enter it.
 *
 * <p>Whitespace is ignored, as the original ignores it, so a pasted column of numbers can still be
 * read when it is a single number.
 */
public final class ClipboardText {

    /** Characters every page accepts: digits, the decimal point, signs and exponent notation. */
    public static final String BASIC = "0123456789+-.e";

    /** The standard page adds multiplication and division. */
    public static final String STANDARD = BASIC + "*/";

    /** The scientific page adds parentheses, the power sign and percent. */
    public static final String SCIENTIFIC = STANDARD + "()^%";

    /** The programmer page adds parentheses and percent, and the hexadecimal letters. */
    public static final String PROGRAMMER = STANDARD + "()%abcdfABCDEF";

    private ClipboardText() {
    }

    /**
     * The characters the given page accepts.
     *
     * @param mode the active calculator page
     */
    public static String alphabet(CalculatorMode mode) {
        return switch (mode) {
            case STANDARD -> STANDARD;
            case SCIENTIFIC -> SCIENTIFIC;
            case PROGRAMMER -> PROGRAMMER;
        };
    }

    /**
     * Whether a text may be pasted on this page.
     *
     * <p>Besides the page's character set, the programmer page is bound by its base: pasting
     * {@code F} into a binary word is refused, because the digit does not exist there.
     *
     * @param text  the clipboard text
     * @param mode  the active calculator page
     * @param radix the programmer base (2, 8, 10 or 16); ignored outside programmer mode
     */
    public static boolean accepts(String text, CalculatorMode mode, int radix) {
        if (text == null || text.isBlank()) {
            return false;
        }
        final String alphabet = alphabet(mode);
        for (int index = 0; index < text.length(); index++) {
            final char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (alphabet.indexOf(c) < 0 && alphabet.indexOf(Character.toLowerCase(c)) < 0) {
                return false;
            }
            if (mode == CalculatorMode.PROGRAMMER && digitValue(c) >= radix) {
                return false;
            }
        }
        return true;
    }

    /**
     * Turns an accepted text into the button tokens that enter it.
     *
     * @param text  the clipboard text, which must be accepted
     * @param mode  the active calculator page
     * @param radix the programmer base
     * @throws IllegalArgumentException when the text contains a character the page does not accept
     */
    public static List<String> tokens(String text, CalculatorMode mode, int radix) {
        Objects.requireNonNull(text, "text");
        if (!accepts(text, mode, radix)) {
            throw new IllegalArgumentException("cannot paste " + text + " on the " + mode + " page");
        }
        final List<String> tokens = new ArrayList<>();
        for (int index = 0; index < text.length(); index++) {
            final char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                continue;
            }
            tokens.add(tokenFor(c, mode));
        }
        return List.copyOf(tokens);
    }

    private static String tokenFor(char c, CalculatorMode mode) {
        if (mode == CalculatorMode.PROGRAMMER) {
            return String.valueOf(c).toLowerCase(Locale.ROOT);
        }
        if (c == 'e' || c == 'E') {
            return "exp";
        }
        return String.valueOf(c);
    }

    /** The numeric value of a digit or hexadecimal letter, or -1 for anything else. */
    private static int digitValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        final char lower = Character.toLowerCase(c);
        if (lower >= 'a' && lower <= 'f') {
            return lower - 'a' + 10;
        }
        return -1;
    }

    /**
     * Whether a token sequence names a button the active page has.
     *
     * <p>Exposed so the caller can refuse a paste before it presses half of it.
     *
     * @param tokens the candidate tokens
     * @param mode   the active calculator page
     */
    public static boolean everyTokenIsAButton(List<String> tokens, CalculatorMode mode) {
        for (String token : tokens) {
            if (Tokens.commandFor(token, mode) == null) {
                return false;
            }
        }
        return true;
    }
}
