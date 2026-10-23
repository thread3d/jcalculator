package dev.jcalculator.diagnostics;

import java.util.Locale;

/** The front end through which a problem was met. */
public enum IssueSurface {

    /** The interactive text interface ({@code CalculatorCli}). */
    CLI("cli"),
    /** The line-oriented JSON harness ({@code HarnessCli}). */
    HARNESS("harness"),
    /** The MCP server ({@code McpServer}). */
    MCP("mcp"),
    /** The Swing window ({@code CalculatorWindow}). */
    SWING("swing");

    private final String wireName;

    IssueSurface(String wireName) {
        this.wireName = wireName;
    }

    /** The stable name used in the journal and on the wire. */
    public String wireName() {
        return wireName;
    }

    /**
     * Resolves a wire name such as {@code "mcp"}.
     *
     * @throws IllegalArgumentException when the name is not a surface
     */
    public static IssueSurface fromWire(String name) {
        final String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        for (IssueSurface surface : values()) {
            if (surface.wireName.equals(normalized) || surface.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return surface;
            }
        }
        throw new IllegalArgumentException("unknown issue surface: " + name);
    }
}
