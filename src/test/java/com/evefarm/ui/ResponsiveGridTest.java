package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResponsiveGridTest {

    @Test
    void asManyCardsSitSideBySideAsFit() {
        assertEquals(3, ResponsiveGrid.columns(1080, 340, 3, 20));
        assertEquals(2, ResponsiveGrid.columns(800, 340, 3, 20));
        assertEquals(1, ResponsiveGrid.columns(200, 340, 3, 20), "one card even when it does not fit");
        assertEquals(2, ResponsiveGrid.columns(2000, 340, 2, 20), "never more columns than cards");
    }

    @Test
    void cardsThatDoNotFitMoveToTheNextRow() {
        JPanel grid = new JPanel(new ResponsiveGrid(20));
        for (int i = 0; i < 3; i++) {
            JPanel card = new JPanel();
            card.setPreferredSize(new Dimension(300, 100 + i * 50));
            grid.add(card);
        }
        grid.setSize(700, 400);
        grid.doLayout();

        assertEquals(new Dimension(340, 150), grid.getComponent(1).getSize());
        assertEquals(360, grid.getComponent(1).getX());
        assertEquals(0, grid.getComponent(2).getX());
        assertEquals(170, grid.getComponent(2).getY(), "below the taller card of the first row");
        assertEquals(370, grid.getPreferredSize().height);
    }
}
