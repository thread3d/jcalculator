package dev.jcalculator.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The preferences the application remembers between runs. */
class SettingsStoreTest {

    @TempDir
    Path directory;

    @Test
    void aFreshInstallGetsTheDefaults() {
        final SettingsStore store = new SettingsStore(directory.resolve("settings.properties"));
        assertEquals(Settings.defaults(), store.load());
        assertEquals(Settings.Theme.SYSTEM, store.load().theme());
        assertFalse(store.load().alwaysOnTop());
    }

    @Test
    void settingsSurviveARestart() {
        final SettingsStore store = new SettingsStore(directory.resolve("nested/settings.properties"));
        assertTrue(store.save(new Settings(Settings.Theme.DARK, true)));

        final SettingsStore reopened = new SettingsStore(directory.resolve("nested/settings.properties"));
        assertEquals(Settings.Theme.DARK, reopened.load().theme());
        assertTrue(reopened.load().alwaysOnTop());
    }

    @Test
    void aDamagedValueFallsBackToTheDefaultRatherThanFailing() throws IOException {
        final Path file = directory.resolve("settings.properties");
        Files.writeString(file, "theme=nonsense\nalwaysOnTop=true\n");

        final Settings loaded = new SettingsStore(file).load();
        assertEquals(Settings.Theme.SYSTEM, loaded.theme());
        assertTrue(loaded.alwaysOnTop());
    }

    @Test
    void anUnreadableFileYieldsTheDefaults() throws IOException {
        final Path file = directory.resolve("settings.properties");
        Files.write(file, new byte[] {0, 1, 2, -1});

        assertEquals(Settings.defaults(), new SettingsStore(file).load());
    }

    @Test
    void aFailedWriteIsReportedNotThrown() {
        final SettingsStore store = new SettingsStore(directory);
        assertFalse(store.save(Settings.defaults()));
    }

    @Test
    void theChoicesCanBeChangedOneAtATime() {
        final Settings settings = Settings.defaults();
        assertEquals(Settings.Theme.LIGHT, settings.withTheme(Settings.Theme.LIGHT).theme());
        assertTrue(settings.withAlwaysOnTop(true).alwaysOnTop());
        assertEquals(Settings.Theme.SYSTEM, settings.withAlwaysOnTop(true).theme());
    }

    @Test
    void theThemeTitlesAreTheOnesTheMenuShows() {
        assertEquals("Light", Settings.Theme.LIGHT.title());
        assertEquals("Dark", Settings.Theme.DARK.title());
        assertEquals("Use system setting", Settings.Theme.SYSTEM.title());
        assertEquals(Settings.Theme.DARK, Settings.Theme.fromWire("dark"));
        assertEquals(Settings.Theme.SYSTEM, Settings.Theme.fromWire(null));
    }
}
