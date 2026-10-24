package dev.jcalculator.app;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;

/**
 * Where {@link Settings} are kept between runs.
 *
 * <p>The location is selected by {@code jcalculator.settings.path} (the launchers pin it beside the
 * issue journal) and defaults to {@code .jcalculator/settings.properties} under the working
 * directory. Reading and writing are best effort in the same sense as the issue journal: a settings
 * file that cannot be read yields the defaults, and one that cannot be written never fails the
 * application.
 */
public final class SettingsStore {

    /** The system property that overrides the settings location. */
    public static final String PATH_PROPERTY = "jcalculator.settings.path";

    private static final String THEME_KEY = "theme";
    private static final String ALWAYS_ON_TOP_KEY = "alwaysOnTop";

    private final Path file;

    /**
     * Opens a store at an explicit file.
     *
     * @param file the properties file; its parent directory is created when the file is written
     */
    public SettingsStore(Path file) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
    }

    /** The store configured for this process. */
    public static SettingsStore shared() {
        final String property = System.getProperty(PATH_PROPERTY);
        if (property != null && !property.isBlank()) {
            return new SettingsStore(Path.of(property));
        }
        return new SettingsStore(Path.of(System.getProperty("user.dir"), ".jcalculator", "settings.properties"));
    }

    /** The file the store reads and writes. */
    public Path file() {
        return file;
    }

    /** Reads the stored settings, or the defaults when the file is absent or unreadable. */
    public Settings load() {
        if (!Files.isRegularFile(file)) {
            return Settings.defaults();
        }
        final Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        } catch (IOException | RuntimeException failure) {
            return Settings.defaults();
        }
        return new Settings(
                Settings.Theme.fromWire(properties.getProperty(THEME_KEY)),
                Boolean.parseBoolean(properties.getProperty(ALWAYS_ON_TOP_KEY, "false")));
    }

    /**
     * Writes the settings, reporting whether they were stored.
     *
     * <p>A failure is returned rather than thrown: a calculator that cannot remember a theme is still
     * a working calculator.
     *
     * @return {@code true} when the file was written
     */
    public boolean save(Settings settings) {
        Objects.requireNonNull(settings, "settings");
        final Properties properties = new Properties();
        properties.setProperty(THEME_KEY, settings.theme().name());
        properties.setProperty(ALWAYS_ON_TOP_KEY, Boolean.toString(settings.alwaysOnTop()));
        try {
            final Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "jCalculator settings");
            }
            return true;
        } catch (IOException | RuntimeException failure) {
            return false;
        }
    }
}
