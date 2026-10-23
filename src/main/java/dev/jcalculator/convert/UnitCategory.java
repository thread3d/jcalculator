package dev.jcalculator.convert;

/** Converter modes, in the order the application's navigation lists them. */
public enum UnitCategory {
    CURRENCY("Currency", false),
    VOLUME("Volume", false),
    LENGTH("Length", false),
    WEIGHT("Weight", false),
    TEMPERATURE("Temperature", true),
    ENERGY("Energy", false),
    AREA("Area", false),
    SPEED("Speed", false),
    TIME("Time", false),
    POWER("Power", true),
    DATA("Data", false),
    PRESSURE("Pressure", false),
    ANGLE("Angle", true);

    private final String displayName;
    private final boolean supportsNegative;

    UnitCategory(String displayName, boolean supportsNegative) {
        this.displayName = displayName;
        this.supportsNegative = supportsNegative;
    }

    public String displayName() {
        return displayName;
    }

    public boolean supportsNegative() {
        return supportsNegative;
    }
}
