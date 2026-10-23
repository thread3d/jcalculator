package dev.jcalculator.convert;

import java.math.BigDecimal;

/**
 * One convertible unit.
 *
 * @param id            the original application's unit id
 * @param category      owning category
 * @param name          English display name
 * @param abbreviation  short symbol shown next to the value
 * @param factor        size of one unit in the category's base unit; {@code null} for units with
 *                      explicit (offset) conversions such as the temperature scales
 */
public record Unit(int id, UnitCategory category, String name, String abbreviation, BigDecimal factor) {

    public boolean hasFactor() {
        return factor != null;
    }
}
