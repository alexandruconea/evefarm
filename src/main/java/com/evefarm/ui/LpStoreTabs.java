package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public final class LpStoreTabs extends JPanel {

    private final LpStorePanel offersPanel;
    private final LpProductionPanel productionPanel;
    private final JTabbedPane tabs = new JTabbedPane();

    public LpStoreTabs(AppContext appContext) {
        super(new BorderLayout());
        offersPanel = new LpStorePanel(appContext);
        productionPanel = new LpProductionPanel(appContext);
        tabs.addTab("Offers", Icons.LP, offersPanel);
        tabs.addTab("LP per Day", Icons.CALENDAR, productionPanel);
        tabs.setToolTipTextAt(1, "The LP your characters earn each day, as a calendar");
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == productionPanel) {
                productionPanel.onShown();
            }
        });
        add(tabs, BorderLayout.CENTER);
    }

    public void onShown() {
        offersPanel.onShown();
        if (tabs.getSelectedComponent() == productionPanel) {
            productionPanel.onShown();
        }
    }

    public void refreshCharacterFilter() {
        offersPanel.refreshCharacterFilter();
        if (tabs.getSelectedComponent() == productionPanel) {
            productionPanel.onShown();
        }
    }
}
