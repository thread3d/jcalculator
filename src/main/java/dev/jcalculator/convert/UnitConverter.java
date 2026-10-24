package dev.jcalculator.convert;

import dev.jcalculator.format.NumberFormatter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Unit and currency conversion, the model behind the converter page.
 *
 * <p>Conversion data is the same data the original application ships: a factor per unit relative to
 * the category's base unit, with explicit offset formulas for the temperature scales. Results are
 * presented the way the converter page presents them: at most 15 significant digits, trimmed of
 * trailing zeros, rounded according to how many digits the input had.
 */
public final class UnitConverter {

    private static final int MAXIMUM_DIGITS = 15;
    private static final int OPTIMAL_DIGITS = 7;
    private static final String MINIMUM_DECIMAL_TEXT = "1e-14";
    private static final String OPTIMAL_DECIMAL_TEXT = "1e-6";

    private static final int TEMPERATURE_CELSIUS = 46;
    private static final int TEMPERATURE_FAHRENHEIT = 47;
    private static final int TEMPERATURE_KELVIN = 48;

    private static MathContext conversionContext() {
        return new MathContext(28, RoundingMode.HALF_EVEN);
    }

    private final Map<UnitCategory, List<Unit>> unitsByCategory = new LinkedHashMap<>();
    private final Map<Integer, BigDecimal> currencyRatios = new LinkedHashMap<>();
    private UnitCategory currentCategory = UnitCategory.CURRENCY;
    private Unit fromUnit;
    private Unit toUnit;

    public UnitConverter() {
        loadUnits();
        currentCategory = UnitCategory.LENGTH;
        fromUnit = findUnit(UnitCategory.LENGTH, "Meter");
        toUnit = findUnit(UnitCategory.LENGTH, "Foot");
    }

    // ---- Catalogue ------------------------------------------------------------------------------

    public List<UnitCategory> categories() {
        return List.of(UnitCategory.values());
    }

    public List<Unit> units(UnitCategory category) {
        return List.copyOf(unitsByCategory.getOrDefault(category, List.of()));
    }

    public UnitCategory currentCategory() {
        return currentCategory;
    }

    public Unit fromUnit() {
        return fromUnit;
    }

    public Unit toUnit() {
        return toUnit;
    }

    public void setCurrentCategory(UnitCategory category) {
        currentCategory = category;
        final List<Unit> units = units(category);
        if (!units.isEmpty()) {
            fromUnit = units.get(0);
            toUnit = units.size() > 1 ? units.get(1) : units.get(0);
        }
    }

    public void setCurrentUnits(Unit from, Unit to) {
        if (from.category() != to.category()) {
            throw new IllegalArgumentException("units belong to different categories");
        }
        currentCategory = from.category();
        fromUnit = from;
        toUnit = to;
    }

    /**
     * Finds a unit by abbreviation, then by display name. An exact match wins over a
     * case-insensitive one, so {@code B} is the byte and {@code b} is the bit.
     */
    public Unit findUnit(UnitCategory category, String abbreviationOrName) {
        for (Unit unit : units(category)) {
            if (unit.abbreviation().equals(abbreviationOrName) || unit.name().equals(abbreviationOrName)) {
                return unit;
            }
        }
        for (Unit unit : units(category)) {
            if (unit.abbreviation().equalsIgnoreCase(abbreviationOrName)
                    || unit.name().equalsIgnoreCase(abbreviationOrName)) {
                return unit;
            }
        }
        throw new IllegalArgumentException("unknown unit in " + category + ": " + abbreviationOrName);
    }

    // ---- Conversion -----------------------------------------------------------------------------

    /** Converts a value between two units of one category. */
    public BigDecimal convert(Unit from, Unit to, BigDecimal value) {
        if (from.category() != to.category()) {
            throw new IllegalArgumentException("cannot convert " + from.category() + " to " + to.category());
        }
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        if (!from.category().supportsNegative() && value.signum() < 0) {
            throw new IllegalArgumentException(from.category() + " does not support negative values");
        }
        if (from.category() == UnitCategory.TEMPERATURE) {
            return convertTemperature(from, to, value);
        }
        BigDecimal ratio;
        if (from.category() == UnitCategory.CURRENCY) {
            ratio = ratioOf(to).divide(ratioOf(from), conversionContext());
        } else {
            if (from.factor() == null || to.factor() == null) {
                throw new IllegalArgumentException("unit lacks a conversion factor");
            }
            ratio = from.factor().divide(to.factor(), conversionContext());
        }
        return value.multiply(ratio, conversionContext());
    }

    public BigDecimal convert(UnitCategory category, String from, String to, BigDecimal value) {
        return convert(findUnit(category, from), findUnit(category, to), value);
    }

    /** Converts and renders the result exactly as the converter page shows it. */
    public String convertAndFormat(Unit from, Unit to, BigDecimal value, String inputText) {
        final BigDecimal result = convert(from, to, value);
        final int inputDigits = inputText == null || inputText.isBlank() ? 1 : NumberFormatter.significantDigits(inputText);
        return formatResult(result, inputDigits, from.category() == UnitCategory.CURRENCY);
    }

    public String convertAndFormat(UnitCategory category, String from, String to, String valueText) {
        final BigDecimal value = new BigDecimal(valueText.trim());
        final Unit source = findUnit(category, from);
        final Unit target = findUnit(category, to);
        return convertAndFormat(source, target, value, valueText);
    }

    /**
     * Renders a conversion result using the converter's digit policy.
     *
     * @param value            converted value
     * @param inputDigits      significant digits the user typed
     * @param currency         currencies keep up to 15 decimal places instead of the optimal policy
     */
    public String formatResult(BigDecimal value, int inputDigits, boolean currency) {
        if (value.signum() == 0) {
            return "0";
        }
        final BigDecimal magnitude = value.abs();
        if (currency) {
            return trim(value.setScale(MAXIMUM_DIGITS, RoundingMode.HALF_UP));
        }
        final int wholeDigits = wholeDigitCount(magnitude);
        if (wholeDigits > MAXIMUM_DIGITS || magnitude.compareTo(new BigDecimal(MINIMUM_DECIMAL_TEXT)) < 0) {
            return String.format(Locale.ROOT, "%s", String.format(Locale.ROOT, "%.6e", value));
        }
        int decimals;
        if (magnitude.compareTo(new BigDecimal(OPTIMAL_DECIMAL_TEXT)) < 0) {
            decimals = MAXIMUM_DIGITS;
        } else {
            final int numberDigits = Math.max(OPTIMAL_DIGITS, Math.min(MAXIMUM_DIGITS, inputDigits));
            decimals = Math.max(0, numberDigits - wholeDigits);
        }
        return trim(value.setScale(decimals, RoundingMode.HALF_UP));
    }

    /** Currency ratio equality, e.g. {@code 1 MAR = 0.5 MON}. */
    /**
     * The converted value expressed in every other unit of the category.
     *
     * <p>This is what the original shows in the supplementary-results area under the converter's
     * answer: the same amount in the units the user is not currently comparing, sorted so the units
     * closest in magnitude to the amount come first, and rounded to two significant digits below one
     * hundred, one below one thousand, and to a whole number above that.
     *
     * @param from  the unit the value is in
     * @param to    the unit the main answer is shown in, which is left out
     * @param value the amount to express in the other units
     */
    public List<SupplementaryValue> supplementaryValues(Unit from, Unit to, BigDecimal value) {
        final List<SupplementaryValue> result = new ArrayList<>();
        if (from == null || value == null) {
            return result;
        }
        final List<ScoredConversion> scored = new ArrayList<>();
        for (Unit unit : units(from.category())) {
            if (unit.id() == from.id() || (to != null && unit.id() == to.id())) {
                continue;
            }
            final BigDecimal converted = convert(from, unit, value);
            scored.add(new ScoredConversion(converted, unit));
        }
        scored.sort((first, second) -> {
            final double firstMagnitude = magnitudeOf(first.converted());
            final double secondMagnitude = magnitudeOf(second.converted());
            final int byDistance = Double.compare(Math.abs(firstMagnitude), Math.abs(secondMagnitude));
            return byDistance != 0 ? byDistance : Double.compare(secondMagnitude, firstMagnitude);
        });
        for (ScoredConversion entry : scored) {
            final String text = roundForSuggestion(entry.converted());
            if (text.isEmpty()) {
                continue;
            }
            if (new BigDecimal(text).signum() != 0 || from.category().supportsNegative()) {
                result.add(new SupplementaryValue(text, entry.unit()));
            }
        }
        return List.copyOf(result);
    }

    private static double magnitudeOf(BigDecimal value) {
        if (value.signum() == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        return Math.log10(value.abs().doubleValue());
    }

    /** Two significant digits below a hundred, one below a thousand, whole numbers above that. */
    private static String roundForSuggestion(BigDecimal value) {
        final BigDecimal magnitude = value.abs();
        if (magnitude.compareTo(BigDecimal.valueOf(100)) < 0) {
            return value.round(new MathContext(2, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString();
        }
        if (magnitude.compareTo(BigDecimal.valueOf(1000)) < 0) {
            return value.round(new MathContext(1, RoundingMode.HALF_UP)).stripTrailingZeros().toPlainString();
        }
        return value.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    /** One conversion in the supplementary list. */
    public record SupplementaryValue(String text, Unit unit) {
    }

    private record ScoredConversion(BigDecimal converted, Unit unit) {
    }

    public String currencyRatioEquality(Unit from, Unit to) {
        final BigDecimal ratio = ratioOf(to).divide(ratioOf(from), conversionContext());
        final String rounded = trim(ratio.setScale(4, RoundingMode.HALF_UP));
        return "1 " + from.abbreviation() + " = " + rounded + " " + to.abbreviation();
    }

    // ---- Data -----------------------------------------------------------------------------------

    private BigDecimal ratioOf(Unit unit) {
        final BigDecimal ratio = currencyRatios.get(unit.id());
        if (ratio == null) {
            throw new IllegalArgumentException("currency has no ratio: " + unit.abbreviation());
        }
        return ratio;
    }

    private BigDecimal convertTemperature(Unit from, Unit to, BigDecimal value) {
        BigDecimal celsius;
        if (from.id() == TEMPERATURE_CELSIUS) {
            celsius = value;
        } else if (from.id() == TEMPERATURE_FAHRENHEIT) {
            celsius = value.subtract(BigDecimal.valueOf(32))
                    .multiply(BigDecimal.valueOf(5))
                    .divide(BigDecimal.valueOf(9), conversionContext());
        } else if (from.id() == TEMPERATURE_KELVIN) {
            celsius = value.subtract(new BigDecimal("273.15"));
        } else {
            throw new IllegalArgumentException("unknown temperature unit");
        }
        if (to.id() == TEMPERATURE_CELSIUS) {
            return celsius;
        }
        if (to.id() == TEMPERATURE_FAHRENHEIT) {
            return celsius.multiply(BigDecimal.valueOf(9))
                    .divide(BigDecimal.valueOf(5), conversionContext())
                    .add(BigDecimal.valueOf(32));
        }
        if (to.id() == TEMPERATURE_KELVIN) {
            return celsius.add(new BigDecimal("273.15"));
        }
        throw new IllegalArgumentException("unknown temperature unit");
    }

    private static int wholeDigitCount(BigDecimal magnitude) {
        if (magnitude.compareTo(BigDecimal.ONE) < 0) {
            return 1;
        }
        return magnitude.precision() - magnitude.scale();
    }

    private static String trim(BigDecimal value) {
        final String text = value.stripTrailingZeros().toPlainString();
        return NumberFormatter.trimTrailingZeros(text);
    }

    private void loadUnits() {
        // Area (base: square metre)
        area(1, "Acre", "acre", "4046.8564224");
        area(2, "Hectare", "ha", "10000");
        area(3, "Square centimeter", "cm²", "0.0001");
        area(4, "Square foot", "ft²", "0.09290304");
        area(5, "Square inch", "in²", "0.00064516");
        area(6, "Square kilometer", "km²", "1000000");
        area(7, "Square meter", "m²", "1");
        area(8, "Square mile", "mi²", "2589988.110336");
        area(9, "Square millimeter", "mm²", "0.000001");
        area(10, "Square yard", "yd²", "0.83612736");
        area(11, "Hand", "hand", "0.012516104");
        area(12, "Paper", "paper", "0.06032246");
        area(13, "Soccer field", "soccer field", "10869.66");
        area(14, "Castle", "castle", "100000");
        area(15, "Pyeong", "pyeong", "3.3057851239669421487603305785124");

        // Data (base: megabyte)
        data(11, "Bit", "b", "0.000000125");
        data(167, "Nibble", "nibble", "0.0000005");
        data(12, "Byte", "B", "0.000001");
        data(15, "Kilobit", "kb", "0.000125");
        data(16, "Kilobyte", "kB", "0.001");
        data(17, "Megabit", "Mb", "0.125");
        data(18, "Megabyte", "MB", "1");
        data(13, "Gigabit", "Gb", "125");
        data(14, "Gigabyte", "GB", "1000");
        data(21, "Terabit", "Tb", "125000");
        data(22, "Terabyte", "TB", "1000000");
        data(19, "Petabit", "Pb", "125000000");
        data(20, "Petabyte", "PB", "1000000000");
        data(143, "Exabit", "Eb", "125000000000");
        data(144, "Exabyte", "EB", "1000000000000");
        data(163, "Zettabit", "Zb", "125000000000000");
        data(164, "Zettabyte", "ZB", "1000000000000000");
        data(159, "Yottabit", "Yb", "125000000000000000");
        data(160, "Yottabyte", "YB", "1000000000000000000");
        data(149, "Kibibit", "Kib", "0.000128");
        data(150, "Kibibyte", "KiB", "0.001024");
        data(151, "Mebibit", "Mib", "0.131072");
        data(152, "Mebibyte", "MiB", "1.048576");
        data(147, "Gibibit", "Gib", "134.217728");
        data(148, "Gibibyte", "GiB", "1073.741824");
        data(155, "Tebibit", "Tib", "137438.953472");
        data(156, "Tebibyte", "TiB", "1099511.627776");
        data(153, "Pebibit", "Pib", "140737488.355328");
        data(154, "Pebibyte", "PiB", "1125899906.842624");
        data(145, "Exbibit", "Eib", "144115188075.855872");
        data(146, "Exbibyte", "EiB", "1152921504606.846976");
        data(161, "Zebibit", "Zib", "147573952589676.412928");
        data(162, "Zebibyte", "ZiB", "1180591620717411.303424");
        data(157, "Yobibit", "Yib", "151115727451828646.838272");
        data(158, "Yobibyte", "YiB", "1208925819614629174.706176");
        data(100, "Floppy disk", "floppy disk", "1.474560");
        data(101, "CD", "CD", "700");
        data(102, "DVD", "DVD", "4700");

        // Energy (base: joule)
        energy(23, "British thermal unit", "BTU", "1055.056");
        energy(24, "Calorie", "cal", "4.184");
        energy(25, "Electron-volt", "eV", "0.0000000000000000001602176565");
        energy(26, "Foot-pound", "ft·lb", "1.3558179483314");
        energy(27, "Joule", "J", "1");
        energy(28, "Kilocalorie", "kcal", "4184");
        energy(29, "Kilojoule", "kJ", "1000");
        energy(166, "Kilowatthour", "kWh", "3600000");
        energy(103, "Battery", "battery", "9000");
        energy(129, "Banana", "banana", "439614");
        energy(130, "Slice of cake", "slice of cake", "1046700");

        // Length (base: metre)
        length(30, "Centimeter", "cm", "0.01");
        length(31, "Foot", "ft", "0.3048");
        length(32, "Inch", "in", "0.0254");
        length(33, "Kilometer", "km", "1000");
        length(34, "Meter", "m", "1");
        length(35, "Micron", "µm", "0.000001");
        length(36, "Mile", "mi", "1609.344");
        length(37, "Millimeter", "mm", "0.001");
        length(38, "Nanometer", "nm", "0.000000001");
        length(39, "Nautical mile", "nmi", "1852");
        length(40, "Yard", "yd", "0.9144");
        length(105, "Paperclip", "paperclip", "0.035052");
        length(131, "Hand", "hand", "0.18669");
        length(107, "Jumbo jet", "jumbo jet", "76");
        length(168, "Angstrom", "Å", "0.0000000001");

        // Power (base: watt)
        power(41, "British thermal unit per minute", "BTU/min", "17.58426666666667");
        power(42, "Foot-pound per minute", "ft·lb/min", "0.0225969658055233");
        power(43, "Horsepower", "hp", "745.69987158227022");
        power(44, "Kilowatt", "kW", "1000");
        power(45, "Watt", "W", "1");
        power(108, "Light bulb", "light bulb", "60");
        power(109, "Horse", "horse", "745.7");
        power(132, "Train engine", "train engine", "2982799.486329081");

        // Time (base: second)
        time(49, "Day", "day", "86400");
        time(50, "Hour", "hour", "3600");
        time(51, "Microsecond", "µs", "0.000001");
        time(52, "Millisecond", "ms", "0.001");
        time(53, "Minute", "minute", "60");
        time(54, "Second", "second", "1");
        time(55, "Week", "week", "604800");
        time(56, "Year", "year", "31557600");

        // Speed (base: centimetre per second)
        speed(57, "Centimeters per second", "cm/s", "1");
        speed(58, "Feet per second", "ft/s", "30.48");
        speed(59, "Kilometers per hour", "km/h", "27.777777777777777777778");
        speed(60, "Knot", "knot", "51.44");
        speed(61, "Mach", "Mach", "34030");
        speed(62, "Meters per second", "m/s", "100");
        speed(63, "Miles per hour", "mph", "44.7");
        speed(121, "Turtle", "turtle", "8.94");
        speed(126, "Horse", "horse", "2011.5");
        speed(122, "Jet", "jet", "24585");

        // Volume (base: cubic centimetre / millilitre)
        volume(64, "Cubic centimeter", "cm³", "1");
        volume(65, "Cubic foot", "ft³", "28316.846592");
        volume(66, "Cubic inch", "in³", "16.387064");
        volume(67, "Cubic meter", "m³", "1000000");
        volume(68, "Cubic yard", "yd³", "764554.857984");
        volume(69, "Cup (US)", "cup (US)", "236.588237");
        volume(70, "Fluid ounce (UK)", "fl oz (UK)", "28.4130625");
        volume(71, "Fluid ounce (US)", "fl oz (US)", "29.5735295625");
        volume(72, "Gallon (UK)", "gal (UK)", "4546.09");
        volume(73, "Gallon (US)", "gal (US)", "3785.411784");
        volume(74, "Liter", "L", "1000");
        volume(75, "Milliliter", "mL", "1");
        volume(76, "Pint (UK)", "pt (UK)", "568.26125");
        volume(77, "Pint (US)", "pt (US)", "473.176473");
        volume(78, "Tablespoon (US)", "tbsp (US)", "14.78676478125");
        volume(79, "Teaspoon (US)", "tsp (US)", "4.92892159375");
        volume(80, "Quart (UK)", "qt (UK)", "1136.5225");
        volume(81, "Quart (US)", "qt (US)", "946.352946");
        volume(115, "Teaspoon (UK)", "tsp (UK)", "5.91938802083333333333");
        volume(116, "Tablespoon (UK)", "tbsp (UK)", "17.7581640625");
        volume(124, "Coffee cup", "coffee cup", "236.5882");
        volume(111, "Bathtub", "bathtub", "378541.2");
        volume(125, "Swimming pool", "swimming pool", "3750000000");

        // Weight (base: kilogram)
        weight(82, "Carat", "ct", "0.0002");
        weight(83, "Centigram", "cg", "0.00001");
        weight(84, "Decigram", "dg", "0.0001");
        weight(85, "Decagram", "dag", "0.01");
        weight(86, "Gram", "g", "0.001");
        weight(87, "Hectogram", "hg", "0.1");
        weight(88, "Kilogram", "kg", "1");
        weight(89, "Long ton", "long ton", "1016.0469088");
        weight(90, "Milligram", "mg", "0.000001");
        weight(91, "Ounce", "oz", "0.028349523125");
        weight(92, "Pound", "lb", "0.45359237");
        weight(93, "Short ton", "short ton", "907.18474");
        weight(94, "Stone", "stone", "6.35029318");
        weight(95, "Tonne", "tonne", "1000");
        weight(113, "Snowflake", "snowflake", "0.000002");
        weight(133, "Soccer ball", "soccer ball", "0.4325");
        weight(114, "Elephant", "elephant", "4000");
        weight(123, "Whale", "whale", "90000");

        // Angle (base: degree)
        angle(134, "Degree", "degree", "1");
        angle(135, "Radian", "radian", "57.29577951308233");
        angle(136, "Gradian", "gradian", "0.9");

        // Pressure (base: atmosphere)
        pressure(137, "Atmosphere", "atm", "1");
        pressure(138, "Bar", "bar", "0.9869232667160128");
        pressure(139, "Kilopascal", "kPa", "0.0098692326671601");
        pressure(140, "Millimeter of mercury", "mmHg", "0.0013155687145324");
        pressure(141, "Pascal", "Pa", "0.000009869232667160128");
        pressure(142, "PSI", "psi", "0.068045961016531");

        // Temperature (explicit conversion; no linear factor)
        temperature(TEMPERATURE_CELSIUS, "Celsius", "C");
        temperature(TEMPERATURE_FAHRENHEIT, "Fahrenheit", "F");
        temperature(TEMPERATURE_KELVIN, "Kelvin", "K");

        // Currency, using the same fictional-planet data as the open-source build.
        currency(171, "Martian Dollar", "MAR", "1.00");
        currency(172, "Moon Bucks", "MON", "0.50");
        currency(173, "Space Coins", "NEP", "0.00125");
        currency(174, "Rings", "SAT", "0.25");
        currency(175, "Galaxy Credits", "URA", "2.75");
        currency(176, "Venusian Seashells", "VEN", "900.00");
        currency(177, "Gas Money", "JUP", "1.23456789123456789");
        currency(178, "Sun Notes", "MER", "2.00");
        currency(179, "Test No Fractional Digits", "JPY", "0.00125");
        currency(180, "Test Fractional Digits", "JOD", "0.25");
    }

    private void add(UnitCategory category, int id, String name, String abbreviation, String factor) {
        unitsByCategory.computeIfAbsent(category, key -> new ArrayList<>())
                .add(new Unit(id, category, name, abbreviation, new BigDecimal(factor)));
    }

    private void area(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.AREA, id, name, abbreviation, factor);
    }

    private void data(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.DATA, id, name, abbreviation, factor);
    }

    private void energy(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.ENERGY, id, name, abbreviation, factor);
    }

    private void length(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.LENGTH, id, name, abbreviation, factor);
    }

    private void power(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.POWER, id, name, abbreviation, factor);
    }

    private void time(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.TIME, id, name, abbreviation, factor);
    }

    private void speed(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.SPEED, id, name, abbreviation, factor);
    }

    private void volume(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.VOLUME, id, name, abbreviation, factor);
    }

    private void weight(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.WEIGHT, id, name, abbreviation, factor);
    }

    private void angle(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.ANGLE, id, name, abbreviation, factor);
    }

    private void pressure(int id, String name, String abbreviation, String factor) {
        add(UnitCategory.PRESSURE, id, name, abbreviation, factor);
    }

    private void temperature(int id, String name, String abbreviation) {
        unitsByCategory.computeIfAbsent(UnitCategory.TEMPERATURE, key -> new ArrayList<>())
                .add(new Unit(id, UnitCategory.TEMPERATURE, name, abbreviation, null));
    }

    private void currency(int id, String name, String abbreviation, String ratio) {
        unitsByCategory.computeIfAbsent(UnitCategory.CURRENCY, key -> new ArrayList<>())
                .add(new Unit(id, UnitCategory.CURRENCY, name, abbreviation, null));
        currencyRatios.put(id, new BigDecimal(ratio));
    }
}
