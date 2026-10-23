package dev.jcalculator.date;

/** Units the date difference can be expressed in. */
public enum DateUnit {
    YEAR(1),
    MONTH(2),
    WEEK(4),
    DAY(8);

    private final int flag;

    DateUnit(int flag) {
        this.flag = flag;
    }

    public int flag() {
        return flag;
    }

    /** Combines units into the bit mask the engine's {@code outputFormat} expects. */
    public static int mask(DateUnit... units) {
        int mask = 0;
        for (DateUnit unit : units) {
            mask |= unit.flag();
        }
        return mask;
    }

    public static final int ALL = YEAR.flag() | MONTH.flag() | WEEK.flag() | DAY.flag();
}
