package dev.jcalculator.app;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionTest {

    @Test
    void versionIsSemantic() {
        assertTrue(AppInfo.VERSION.matches("\\d+\\.\\d+\\.\\d+"),
                "version must be MAJOR.MINOR.PATCH but was " + AppInfo.VERSION);
    }

    @Test
    void versionMatchesTheMavenProjectVersion() throws IOException {
        final Path pom = Path.of(System.getProperty("basedir", "."), "pom.xml");
        final String text = Files.readString(pom);
        assertTrue(text.contains("<version>" + AppInfo.VERSION + "</version>"),
                "pom.xml and AppInfo.VERSION disagree; update both to the same release version");
    }
}
