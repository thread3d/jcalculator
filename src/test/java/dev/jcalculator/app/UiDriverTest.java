package dev.jcalculator.app;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiDriverTest {

    @Test
    void clicksThroughTheRealButtons() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        try (UiDriver driver = UiDriver.launch()) {
            driver.click("2");
            driver.click("+");
            driver.click("3");
            driver.click("=");
            assertEquals("5", driver.display());
            assertEquals("2 + 3=", driver.expression());
            assertEquals(1, driver.window().surface().history().size());
            assertFalse(driver.isError());
        }
    }

    @Test
    void switchingModeRebuildsTheKeypad() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        try (UiDriver driver = UiDriver.launch()) {
            assertTrue(driver.buttons().contains("CE"));
            driver.selectMode("scientific");
            final List<String> labels = driver.buttons();
            assertTrue(labels.contains("sin"));
            assertTrue(labels.contains("xʸ"));
            assertFalse(labels.contains("AND"));
            driver.click("sin");
            assertEquals("0", driver.display());
        }
    }

    @Test
    void errorsAreRenderedInRedText() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        try (UiDriver driver = UiDriver.launch()) {
            driver.click("1");
            driver.click("÷");
            driver.click("0");
            driver.click("=");
            assertTrue(driver.isError());
            assertEquals("Cannot divide by zero", driver.display());
            assertTrue(driver.snapshot().error());
        }
    }

    @Test
    void keyboardStylePressesWorkToo() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        try (UiDriver driver = UiDriver.launch()) {
            driver.type("12");
            driver.press("+");
            driver.type("30");
            driver.press("=");
            assertEquals("42", driver.display());
        }
    }

    @Test
    void buttonsCarryTheirLabelsAsAccessibleNames() {
        Assumptions.assumeTrue(UiDriver.isSupported(), "no display available");
        try (UiDriver driver = UiDriver.launch()) {
            final JButton seven = driver.window().findButton("7").orElseThrow();
            assertEquals("7", seven.getName());
            assertEquals("7", seven.getAccessibleContext().getAccessibleName());
            assertEquals("Display", driver.window().displayAccessibleName());
        }
    }
}
