package dev.jcalculator.diagnostics;

import dev.jcalculator.app.AppInfo;
import dev.jcalculator.engine.CalculatorMode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The drafts a front end records when something goes wrong without being asked to.
 *
 * <p>Automatic reports are deliberately narrow: malformed caller input is the caller's problem and
 * is reported as an error response, not journalled. What is journalled is a token the tool refuses
 * (the vocabulary and the caller disagree), an operation the protocol does not implement (the
 * documentation and the implementation disagree), and a failure the model did not expect.
 *
 * <p>The summary and the context signature together are the issue's identity, so the same problem
 * folds into one entry however often it recurs.
 */
public final class Faults {

    private static final int MAX_STACK_CHARS = 8_000;

    private Faults() {
    }

    /** A token that is not a button on the active page. */
    public static IssueDraft unknownToken(IssueSurface surface, String token, CalculatorMode mode) {
        Objects.requireNonNull(surface, "surface");
        final String name = token == null ? "" : token;
        final String page = mode == null ? "unknown" : mode.name();
        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("signature", "token=" + name + "|mode=" + page);
        context.put("token", name);
        context.put("mode", page);
        context.put("version", AppInfo.VERSION);
        return new IssueDraft(
                IssueKind.UNSUPPORTED_INPUT,
                IssueSeverity.LOW,
                surface,
                "unknown token: " + name,
                "A token that is not a calculator button was pressed and was refused;"
                        + " no part of that press was applied.",
                context);
    }

    /** An operation the protocol does not implement. */
    public static IssueDraft unknownOperation(IssueSurface surface, String operation) {
        Objects.requireNonNull(surface, "surface");
        final String name = operation == null ? "" : operation;
        final Map<String, Object> context = new LinkedHashMap<>();
        context.put("signature", "op=" + name);
        context.put("operation", name);
        context.put("version", AppInfo.VERSION);
        return new IssueDraft(
                IssueKind.UNSUPPORTED_OPERATION,
                IssueSeverity.LOW,
                surface,
                "unknown operation: " + name,
                "A caller asked for an operation the protocol does not implement.",
                context);
    }

    /**
     * A failure the calculator did not expect.
     *
     * @param surface the front end that met the failure
     * @param error   the failure itself; its stack trace is stored, bounded
     * @param context reproduction facts such as the request and the calculator state
     */
    public static IssueDraft internalError(IssueSurface surface, Throwable error, Map<String, Object> context) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(error, "error");
        final Map<String, Object> details = new LinkedHashMap<>(Objects.requireNonNull(context, "context"));
        final String exceptionName = error.getClass().getName();
        final String frame = topFrame(error);
        details.put("signature", exceptionName + "|" + frame);
        details.put("exception", exceptionName);
        details.put("exceptionMessage", error.getMessage() == null ? "" : error.getMessage());
        details.put("topFrame", frame);
        details.put("version", AppInfo.VERSION);
        final String message = error.getMessage();
        final String summary = "internal error: " + error.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return new IssueDraft(
                IssueKind.INTERNAL_ERROR,
                IssueSeverity.HIGH,
                surface,
                summary,
                stackTrace(error),
                details);
    }

    private static String topFrame(Throwable error) {
        final StackTraceElement[] trace = error.getStackTrace();
        if (trace.length == 0) {
            return error.getClass().getName();
        }
        return trace[0].getClassName() + "." + trace[0].getMethodName();
    }

    private static String stackTrace(Throwable error) {
        final StringBuilder text = new StringBuilder(512);
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8 && text.length() <= MAX_STACK_CHARS) {
            text.append(current).append('\n');
            for (StackTraceElement frame : current.getStackTrace()) {
                text.append("\tat ").append(frame).append('\n');
                if (text.length() > MAX_STACK_CHARS) {
                    break;
                }
            }
            current = current.getCause();
            depth++;
        }
        if (text.length() <= MAX_STACK_CHARS) {
            return text.toString();
        }
        return text.substring(0, MAX_STACK_CHARS) + "\n... (stack trace truncated)";
    }
}
