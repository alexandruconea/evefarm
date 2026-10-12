package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateDialogTest {

    private static JCheckBox box(boolean ticked, boolean enabled) {
        JCheckBox box = new JCheckBox("Assets", ticked);
        box.setEnabled(enabled);
        return box;
    }

    @Test
    void allIsTickedWhenEveryLineThatCanUpdateIsTicked() {
        assertTrue(UpdateDialog.allTicked(List.of(box(true, true), box(true, true))));
        assertTrue(UpdateDialog.allTicked(List.of(box(true, true), box(false, false))),
                "a line that is still waiting doesn't count");
    }

    @Test
    void allIsClearWhenALineIsClearOrNothingCanUpdate() {
        assertFalse(UpdateDialog.allTicked(List.of(box(true, true), box(false, true))));
        assertFalse(UpdateDialog.allTicked(List.of(box(true, false), box(true, false))));
        assertFalse(UpdateDialog.allTicked(List.of()));
    }
}
