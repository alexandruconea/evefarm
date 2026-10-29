package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public final class LpStoreTabs extends TabPanel {

    private final LpStorePanel offersPanel;
    private final LpPurchasesPanel purchasesPanel;
    private final LpProductionPanel productionPanel;
    private final MissionReportPanel reportPanel;
    private final JTabbedPane tabs = new JTabbedPane();

    public LpStoreTabs(AppContext appContext) {
        super(new BorderLayout());
        offersPanel = new LpStorePanel(appContext);
        purchasesPanel = new LpPurchasesPanel(appContext);
        productionPanel = new LpProductionPanel(appContext);
        reportPanel = new MissionReportPanel(appContext);
        tabs.addTab("Offers", Icons.LP, offersPanel);
        tabs.addTab("Purchases", Icons.PACKAGE, purchasesPanel);
        tabs.setToolTipTextAt(1, "What you bought in LP stores and the price you need to sell it without a loss");
        tabs.addTab("LP per Day", Icons.CALENDAR, productionPanel);
        tabs.setToolTipTextAt(2, "The LP your characters earn each day, as a calendar");
        tabs.addTab("Report", Icons.DOCUMENT, reportPanel);
        tabs.setToolTipTextAt(3, "The LP and the ISK from missions of a day, a week or a month");
        tabs.addChangeListener(e -> showSelected());
        add(tabs, BorderLayout.CENTER);
    }

    @Override
    public void onShown() {
        offersPanel.onShown();
        showSelected();
    }

    @Override
    public void refreshCharacterFilter() {
        offersPanel.refreshCharacterFilter();
        showSelected();
    }

    private void showSelected() {
        if (tabs.getSelectedComponent() == purchasesPanel) {
            purchasesPanel.onShown();
        } else if (tabs.getSelectedComponent() == productionPanel) {
            productionPanel.onShown();
        } else if (tabs.getSelectedComponent() == reportPanel) {
            reportPanel.onShown();
        }
    }
}
