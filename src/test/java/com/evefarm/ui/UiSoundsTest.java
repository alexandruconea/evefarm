package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.Component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiSoundsTest {

    private static int peak(byte[] data) {
        int peak = 0;
        for (int i = 0; i < data.length; i += 2) {
            peak = Math.max(peak, Math.abs((short) ((data[i] & 0xFF) | (data[i + 1] << 8))));
        }
        return peak;
    }

    private static int last(byte[] data) {
        return Math.abs((short) ((data[data.length - 2] & 0xFF) | (data[data.length - 1] << 8)));
    }

    @Test
    void theTickIsShortSoftAndFadesOut() {
        byte[] hover = UiSounds.hover(100);
        byte[] click = UiSounds.click(100);

        assertEquals(Math.round(UiSounds.SAMPLE_RATE * 0.020) * 2, hover.length, 2);
        assertTrue(peak(hover) < peak(click), "the hover tick is softer than the click");
        assertTrue(peak(click) < Short.MAX_VALUE / 2, "even at full volume it stays quiet");
        assertTrue(last(hover) < 50 && last(click) < 50, "both fade out without a pop at the end");
    }

    @Test
    void theVolumeScalesTheSoundAndZeroIsSilent() {
        assertTrue(peak(UiSounds.click(40)) < peak(UiSounds.click(80)));
        assertEquals(0, peak(UiSounds.click(0)));
    }

    @Test
    void buttonsTabsAndListsMakeASoundButNotTheirParts() {
        JButton button = new JButton("Save");
        JButton disabled = new JButton("Delete");
        disabled.setEnabled(false);
        JComboBox<String> combo = new JComboBox<>(new String[]{"Week"});
        JPanel tab = new JPanel();
        tab.putClientProperty(UiSounds.SOUND_TARGET, true);
        JLabel tabTitle = new JLabel("Values");
        tab.add(tabTitle);
        JSpinner spinner = new JSpinner();

        assertSame(button, UiSounds.target(button));
        assertNull(UiSounds.target(disabled));
        assertSame(tab, UiSounds.target(tabTitle));
        for (Component part : combo.getComponents()) {
            assertSame(combo, UiSounds.target(part));
        }
        for (Component part : spinner.getComponents()) {
            assertNull(UiSounds.target(part));
        }
        assertNull(UiSounds.target(new JLabel("Plain text")));
    }
}
