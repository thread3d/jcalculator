package dev.jcalculator.convert;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnitConverterTest {

    private final UnitConverter converter = new UnitConverter();

    private String convert(String category, String from, String to, String value) {
        return converter.convertAndFormat(
                UnitCategory.valueOf(category), from, to, value);
    }

    @Test
    void catalogueHasEveryCategoryAndUnits() {
        assertTrue(converter.categories().contains(UnitCategory.LENGTH));
        assertTrue(converter.units(UnitCategory.LENGTH).size() >= 10);
        assertTrue(converter.units(UnitCategory.CURRENCY).size() >= 10);
        assertEquals("Foot", converter.findUnit(UnitCategory.LENGTH, "ft").name());
    }

    @Test
    void convertsLengthExactly() {
        assertEquals("12", convert("LENGTH", "ft", "in", "1"));
        assertEquals("100", convert("LENGTH", "m", "cm", "1"));
        assertEquals("1.609344", convert("LENGTH", "mi", "km", "1"));
        assertEquals("3.28084", convert("LENGTH", "m", "ft", "1"));
        assertEquals("8.2021", convert("LENGTH", "m", "ft", "2.5"));
    }

    @Test
    void convertsWeightAndVolume() {
        assertEquals("11.02311", convert("WEIGHT", "kg", "lb", "5"));
        assertEquals("1000", convert("VOLUME", "L", "mL", "1"));
        assertEquals("8", convert("DATA", "B", "b", "1"));
        assertEquals("1000", convert("DATA", "MB", "kB", "1"));
    }

    @Test
    void convertsTemperatureWithOffsets() {
        assertEquals("212", convert("TEMPERATURE", "C", "F", "100"));
        assertEquals("0", convert("TEMPERATURE", "F", "C", "32"));
        assertEquals("273.15", convert("TEMPERATURE", "C", "K", "0"));
        assertEquals("37", convert("TEMPERATURE", "F", "C", "98.6"));
        assertEquals("-40", convert("TEMPERATURE", "F", "C", "-40"));
    }

    @Test
    void convertsCurrencyWithTheMockRatios() {
        assertEquals("0.5", convert("CURRENCY", "MAR", "MON", "1"));
        assertEquals("2", convert("CURRENCY", "MON", "MAR", "1"));
        assertEquals("450", convert("CURRENCY", "MAR", "VEN", "0.5"));
    }

    @Test
    void temperatureSupportsNegativeValuesButOtherCategoriesDoNot() {
        assertThrows(IllegalArgumentException.class, () -> convert("LENGTH", "m", "ft", "-1"));
        assertEquals("-40", convert("TEMPERATURE", "C", "F", "-40"));
    }

    @Test
    void unitsOfDifferentCategoriesCannotBeMixed() {
        final Unit meter = converter.findUnit(UnitCategory.LENGTH, "m");
        final Unit kilogram = converter.findUnit(UnitCategory.WEIGHT, "kg");
        assertThrows(IllegalArgumentException.class,
                () -> converter.convert(meter, kilogram, BigDecimal.ONE));
    }

    @Test
    void unknownUnitIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> converter.findUnit(UnitCategory.LENGTH, "parsec"));
    }

    @Test
    void displayRoundsToTheOptimalNumberOfDigits() {
        final BigDecimal value = new BigDecimal("1.234567890123456789");
        assertEquals("1.234568", converter.formatResult(value, 1, false));
        assertEquals("1.234567890123457", converter.formatResult(value, 1, true));
        assertEquals("1", converter.formatResult(new BigDecimal("1.000000"), 1, false));
        assertEquals("0", converter.formatResult(BigDecimal.ZERO, 4, false));
    }

    @Test
    void tinyValuesUseScientificNotation() {
        final BigDecimal value = new BigDecimal("1.234e-16");
        assertEquals("1.234000e-16", converter.formatResult(value, 1, false));
    }

    @Test
    void supplementaryValuesExpressTheAmountInTheOtherUnits() {
        final Unit meter = converter.findUnit(UnitCategory.LENGTH, "Meter");
        final Unit foot = converter.findUnit(UnitCategory.LENGTH, "Foot");

        final List<UnitConverter.SupplementaryValue> values =
                converter.supplementaryValues(meter, foot, BigDecimal.ONE);

        assertFalse(values.isEmpty());
        assertTrue(values.stream().noneMatch(value -> value.unit().abbreviation().equals("m")));
        assertTrue(values.stream().noneMatch(value -> value.unit().abbreviation().equals("ft")));
        assertTrue(values.stream().anyMatch(value -> value.text().equals("39")));
    }

    @Test
    void supplementaryValuesPutTheClosestMagnitudeFirst() {
        final Unit meter = converter.findUnit(UnitCategory.LENGTH, "Meter");
        final Unit foot = converter.findUnit(UnitCategory.LENGTH, "Foot");
        final List<UnitConverter.SupplementaryValue> values =
                converter.supplementaryValues(meter, foot, BigDecimal.ONE);

        assertEquals("Yard", values.get(0).unit().name());
        assertEquals("1.1", values.get(0).text());
    }

    @Test
    void supplementaryValuesRoundToAFewSignificantDigits() {
        final Unit celsius = converter.findUnit(UnitCategory.TEMPERATURE, "Celsius");
        final Unit fahrenheit = converter.findUnit(UnitCategory.TEMPERATURE, "Fahrenheit");
        final List<UnitConverter.SupplementaryValue> values =
                converter.supplementaryValues(celsius, fahrenheit, BigDecimal.ZERO);

        assertEquals(1, values.size());
        assertEquals("Kelvin", values.get(0).unit().name());
        assertEquals("300", values.get(0).text());
    }

    @Test
    void thereAreNoSupplementaryValuesWithoutAUnit() {
        assertTrue(converter.supplementaryValues(null, null, BigDecimal.ONE).isEmpty());
        final Unit meter = converter.findUnit(UnitCategory.LENGTH, "Meter");
        assertTrue(converter.supplementaryValues(meter, null, null).isEmpty());
    }

    @Test
    void aUnitKnowsWhetherItConvertsByAFactor() {
        final Unit metre = new Unit(1, UnitCategory.LENGTH, "Metre", "m", BigDecimal.ONE);
        assertTrue(metre.hasFactor());
        assertEquals(UnitCategory.LENGTH, metre.category());
        assertEquals("m", metre.abbreviation());

        final Unit celsius = new Unit(2, UnitCategory.TEMPERATURE, "Celsius", "C", null);
        assertFalse(celsius.hasFactor());
    }

    @Test
    void settingCategoryPicksSensibleDefaults() {
        converter.setCurrentCategory(UnitCategory.TEMPERATURE);
        assertEquals(UnitCategory.TEMPERATURE, converter.currentCategory());
        assertEquals("C", converter.fromUnit().abbreviation());
        assertEquals("F", converter.toUnit().abbreviation());
    }
}
