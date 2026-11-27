package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputsTest {

    @Test
    void amountsHaveNoArrowsAndUseThousandsSeparators() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(1_234_567.5, 0.0, 1e15, 1_000.0));

        Inputs.amount(spinner, "ISK");

        assertFalse(Arrays.stream(spinner.getComponents()).anyMatch(c -> c.getName() != null
                && c.getName().startsWith("Spinner.") && c.getName().endsWith("Button")));
        assertEquals("1,234,567.5", ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField().getText());
    }

    @Test
    void aWrongValueIsMarkedWhileTypingAndClearedWhenFixed() {
        JTextField field = new JTextField();
        Inputs.check(field, text -> text.matches("\\d+"));

        field.setText("12a");
        assertTrue(Inputs.isMarked(field));

        field.setText("12");
        assertFalse(Inputs.isMarked(field));

        field.setText("");
        assertFalse(Inputs.isMarked(field), "an empty field isn't wrong");
    }

    @Test
    void aRejectedValueStaysMarkedUntilItIsEdited() {
        JTextField field = new JTextField("secret");

        Inputs.reject(field, "Too short");
        assertTrue(Inputs.isMarked(field));

        field.setText("a longer secret");
        assertFalse(Inputs.isMarked(field));
    }
}
