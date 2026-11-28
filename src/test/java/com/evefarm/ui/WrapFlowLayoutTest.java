package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WrapFlowLayoutTest {

    @Test
    void whatDoesNotFitGoesToASecondLineThatIsCountedInTheHeight() {
        JPanel row = new JPanel(new WrapFlowLayout(6, 6));
        for (int i = 0; i < 3; i++) {
            JPanel part = new JPanel();
            part.setPreferredSize(new Dimension(120, 30));
            row.add(part);
        }

        assertEquals(new Dimension(384, 42), row.getPreferredSize(), "one line before it has a width");

        row.setSize(300, 100);
        row.doLayout();

        assertEquals(new Point(6, 42), row.getComponent(2).getLocation());
        assertEquals(78, row.getPreferredSize().height);
    }
}
