package dev.jcalculator.app;

import java.util.Locale;
import java.util.Objects;

/**
 * The preferences the application keeps between runs.
 *
 * <p>The original's settings page offers light, dark and follow-the-system themes, and its title bar
 * offers an always-on-top mode that remembers the window size. This record is the same two choices,
 * without a view attached.
 *
 * @param theme       which colour scheme to use
 * @param alwaysOnTop whether the window stays above other windows
 */
public record Settings(Theme theme, boolean alwaysOnTop) {

    /** The colour scheme choices, named as the settings page names them. */
    public enum Theme {
        LIGHT("Light"),
        DARK("Dark"),
        SYSTEM("Use system setting");

        private final String title;

        Theme(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }

        /**
         * Resolves a stored value, falling back to {@link #SYSTEM} rather than failing.
         *
         * <p>A settings file is user data: a corrupted or hand-edited value must not stop the
         * application from starting.
         */
        public static Theme fromWire(String value) {
            if (value == null) {
                return SYSTEM;
            }
            final String normalized = value.trim().toUpperCase(Locale.ROOT);
            for (Theme theme : values()) {
                if (theme.name().equals(normalized)) {
                    return theme;
                }
            }
            return SYSTEM;
        }
    }

    public Settings {
        Objects.requireNonNull(theme, "theme");
    }

    /** The settings a fresh installation starts with. */
    public static Settings defaults() {
        return new Settings(Theme.SYSTEM, false);
    }

    public Settings withTheme(Theme newTheme) {
        return new Settings(newTheme, alwaysOnTop);
    }

    public Settings withAlwaysOnTop(boolean onTop) {
        return new Settings(theme, onTop);
    }
}
