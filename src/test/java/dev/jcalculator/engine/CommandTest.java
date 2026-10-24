package dev.jcalculator.engine;

import dev.jcalculator.core.AngleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The command table: how a command is classified, and how it is spelled in the expression line. */
class CommandTest {

    @Test
    void digitsCarryTheirValue() {
        assertTrue(Command.ZERO.isDigit());
        assertEquals(0, Command.ZERO.digitValue());
        assertEquals(9, Command.NINE.digitValue());
        assertEquals(10, Command.A.digitValue());
        assertEquals(15, Command.F.digitValue());
        assertFalse(Command.ADD.isDigit());
        assertThrows(IllegalStateException.class, Command.ADD::digitValue);
        assertEquals(10, Command.digits().size());
    }

    @Test
    void commandsAreClassifiedByKind() {
        assertTrue(Command.ADD.isBinary());
        assertFalse(Command.SIN.isBinary());
        assertTrue(Command.SIN.isUnary());
        assertTrue(Command.ROTATE_LEFT_CARRY.isUnary());
        assertFalse(Command.ADD.isUnary());
        assertTrue(Command.OPEN_PAREN.isParenthesis());
        assertTrue(Command.CLOSE_PAREN.isParenthesis());
        assertFalse(Command.ADD.isParenthesis());
        assertTrue(Command.RADIX_16.isGuiSetting());
        assertTrue(Command.BACKSPACE.isGuiSetting());
        assertFalse(Command.MEMORY_RECALL.isGuiSetting());
        assertTrue(Command.MEMORY_CLEAR.isMemoryCommand());
        assertFalse(Command.ADD.isMemoryCommand());
    }

    @Test
    void aBinaryOperatorIsSpelledForThePage() {
        assertEquals("+", Command.ADD.expressionToken(false));
        assertEquals("-", Command.SUBTRACT.expressionToken(false));
        assertEquals("×", Command.MULTIPLY.expressionToken(false));
        assertEquals("÷", Command.DIVIDE.expressionToken(false));
        assertEquals("^", Command.POWER.expressionToken(false));
        assertEquals("yroot", Command.ROOT.expressionToken(false));
        assertEquals("log base", Command.LOGBASEY.expressionToken(false));
        assertEquals("mod", Command.MOD.expressionToken(false));
        assertEquals("MOD", Command.MOD.expressionToken(true));
        assertEquals("AND", Command.AND.expressionToken(true));
        assertEquals("OR", Command.OR.expressionToken(true));
        assertEquals("XOR", Command.XOR.expressionToken(true));
        assertEquals("NAND", Command.NAND.expressionToken(true));
        assertEquals("NOR", Command.NOR.expressionToken(true));
        assertEquals("Lsh", Command.LEFT_SHIFT.expressionToken(true));
        assertEquals("Rsh", Command.RIGHT_SHIFT.expressionToken(true));
        assertEquals("Rsh logical", Command.RIGHT_SHIFT_LOGICAL.expressionToken(true));
        assertEquals("ROL", Command.ROTATE_LEFT.expressionToken(true));
        assertEquals("ROR", Command.ROTATE_RIGHT.expressionToken(true));
        assertEquals("ROL carry", Command.ROTATE_LEFT_CARRY.expressionToken(true));
        assertEquals("ROR carry", Command.ROTATE_RIGHT_CARRY.expressionToken(true));
        assertEquals("equals", Command.EQUALS.expressionToken(false));
    }

    @Test
    void aFunctionIsSpelledWithItsAngleUnitAndInverseMark() {
        assertEquals("sin°", Command.SIN.unaryName(false, AngleType.DEGREES));
        assertEquals("sin rad", Command.SIN.unaryName(false, AngleType.RADIANS));
        assertEquals("sin grad", Command.SIN.unaryName(false, AngleType.GRADIANS));
        assertEquals("sin⁻¹°", Command.SIN.unaryName(true, AngleType.DEGREES));
        assertEquals("cos⁻¹", Command.ACOS.unaryName(false, AngleType.DEGREES));
        assertEquals("cos⁻¹°", Command.COS.unaryName(true, AngleType.DEGREES));
        assertEquals("tan⁻¹", Command.ATAN.unaryName(false, AngleType.DEGREES));
        assertEquals("sin⁻¹", Command.ASIN.unaryName(false, AngleType.DEGREES));
        assertEquals("tanh⁻¹", Command.ATANH.unaryName(false, AngleType.DEGREES));
        assertEquals("sec⁻¹", Command.ASEC.unaryName(false, AngleType.DEGREES));
        assertEquals("coth⁻¹", Command.ACOTH.unaryName(false, AngleType.DEGREES));
        assertEquals("tanh", Command.TANH.unaryName(false, AngleType.DEGREES));
        assertEquals("sinh⁻¹", Command.ASINH.unaryName(false, AngleType.DEGREES));
        assertEquals("e^", Command.LN.unaryName(true, AngleType.DEGREES));
        assertEquals("ln", Command.LN.unaryName(false, AngleType.DEGREES));
        assertEquals("log", Command.LOG.unaryName(false, AngleType.DEGREES));
        assertEquals("√", Command.SQRT.unaryName(false, AngleType.DEGREES));
        assertEquals("sqr", Command.SQR.unaryName(false, AngleType.DEGREES));
        assertEquals("cube", Command.CUBE.unaryName(false, AngleType.DEGREES));
        assertEquals("cuberoot", Command.CUBEROOT.unaryName(false, AngleType.DEGREES));
        assertEquals("fact", Command.FACTORIAL.unaryName(false, AngleType.DEGREES));
        assertEquals("reciproc", Command.RECIPROCAL.unaryName(false, AngleType.DEGREES));
        assertEquals("dms", Command.DMS.unaryName(false, AngleType.DEGREES));
        assertEquals("degrees", Command.DMS.unaryName(true, AngleType.DEGREES));
        assertEquals("degrees", Command.DEGREES.unaryName(false, AngleType.DEGREES));
        assertEquals("10^", Command.POW10.unaryName(false, AngleType.DEGREES));
        assertEquals("2^", Command.POW2.unaryName(false, AngleType.DEGREES));
        assertEquals("e^", Command.POW_E.unaryName(false, AngleType.DEGREES));
        assertEquals("integer", Command.INTEGER_PART.unaryName(false, AngleType.DEGREES));
        assertEquals("frac", Command.INTEGER_PART.unaryName(true, AngleType.DEGREES));
        assertEquals("abs", Command.ABS.unaryName(false, AngleType.DEGREES));
        assertEquals("ceil", Command.CEIL.unaryName(false, AngleType.DEGREES));
        assertEquals("floor", Command.FLOOR.unaryName(false, AngleType.DEGREES));
        assertEquals("negate", Command.NEGATE.unaryName(false, AngleType.DEGREES));
    }

    @Test
    void aUnaryFunctionWrapsItsOperand() {
        assertEquals("sin°(30)", Command.SIN.unaryExpression("30", false, AngleType.DEGREES));
        assertEquals("sec⁻¹ rad", Command.SEC.unaryName(true, AngleType.RADIANS));
        assertEquals("csc rad", Command.CSC.unaryName(false, AngleType.RADIANS));
        assertEquals("cot grad", Command.COT.unaryName(false, AngleType.GRADIANS));
    }
}
