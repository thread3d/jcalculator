package dev.jcalculator.app;

/**
 * Application identity.
 *
 * <p>The version is semantic: {@code MAJOR.MINOR.PATCH}. It is kept in step with the Maven project
 * version in {@code pom.xml}; {@code VersionTest} fails the build if the two drift apart. The jar
 * manifest and the {@code --version} command report the same value.
 */
public final class AppInfo {

    public static final String NAME = "jCalculator";
    public static final String VERSION = "2.3.0";

    private AppInfo() {
    }

    /** {@code jCalculator 2.3.0}, the form used by the command-line banner. */
    public static String displayVersion() {
        return NAME + " " + VERSION;
    }
}
