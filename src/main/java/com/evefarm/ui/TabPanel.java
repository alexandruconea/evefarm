package com.evefarm.ui;

import javax.swing.JPanel;
import java.awt.LayoutManager;

abstract class TabPanel extends JPanel {

    TabPanel() {
    }

    TabPanel(LayoutManager layout) {
        super(layout);
    }

    abstract void onShown();

    void refreshCharacterFilter() {
    }

    void onDataUpdated(boolean selected) {
        onShown();
    }
}
