package dev.jcalculator.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Binding power and the operator-replacement rule the scientific page applies. */
class OperatorPrecedenceTest {

    @Test
    void eachOperatorHasItsLevel() {
        assertEquals(0, OperatorPrecedence.of(null));
        assertEquals(0, OperatorPrecedence.of(Command.OR));
        assertEquals(0, OperatorPrecedence.of(Command.XOR));
        assertEquals(1, OperatorPrecedence.of(Command.AND));
        assertEquals(1, OperatorPrecedence.of(Command.NAND));
        assertEquals(1, OperatorPrecedence.of(Command.NOR));
        assertEquals(2, OperatorPrecedence.of(Command.ADD));
        assertEquals(2, OperatorPrecedence.of(Command.SUBTRACT));
        assertEquals(3, OperatorPrecedence.of(Command.MULTIPLY));
        assertEquals(3, OperatorPrecedence.of(Command.DIVIDE));
        assertEquals(3, OperatorPrecedence.of(Command.MOD));
        assertEquals(3, OperatorPrecedence.of(Command.LEFT_SHIFT));
        assertEquals(3, OperatorPrecedence.of(Command.RIGHT_SHIFT));
        assertEquals(3, OperatorPrecedence.of(Command.RIGHT_SHIFT_LOGICAL));
        assertEquals(4, OperatorPrecedence.of(Command.POWER));
        assertEquals(4, OperatorPrecedence.of(Command.ROOT));
        assertEquals(4, OperatorPrecedence.of(Command.LOGBASEY));
    }

    @Test
    void aCommandThatIsNotAnOperatorHasNoBindingPower() {
        assertEquals(0, OperatorPrecedence.of(Command.SIN));
        assertEquals(0, OperatorPrecedence.of(Command.EQUALS));
    }

    @Test
    void anOperatorPressedOverAPendingOneInvertsPrecedenceOnlyWhenItBindsTighter() {
        // 1 x 2 + then ^ : the pending + binds no tighter than the x before it, and ^ binds tighter.
        assertTrue(OperatorPrecedence.inverts(3, 2, 4));
        assertTrue(OperatorPrecedence.inverts(3, 3, 4));
        // A weaker or equal incoming operator replaces without enclosing anything.
        assertFalse(OperatorPrecedence.inverts(2, 2, 1));
        assertFalse(OperatorPrecedence.inverts(3, 2, 1));
        assertFalse(OperatorPrecedence.inverts(4, 3, 4));
    }
}
