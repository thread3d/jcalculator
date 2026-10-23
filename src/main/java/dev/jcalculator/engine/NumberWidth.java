package dev.jcalculator.engine;

/** Word widths of the programmer calculator, matching QWORD/DWORD/WORD/BYTE. */
public enum NumberWidth {
    QWORD(64, "9223372036854775807"),
    DWORD(32, "2147483647"),
    WORD(16, "32767"),
    BYTE(8, "127");

    private final int bits;
    private final String maxDecimalValue;

    NumberWidth(int bits, String maxDecimalValue) {
        this.bits = bits;
        this.maxDecimalValue = maxDecimalValue;
    }

    public int bits() {
        return bits;
    }

    public String maxDecimalValue() {
        return maxDecimalValue;
    }
}
