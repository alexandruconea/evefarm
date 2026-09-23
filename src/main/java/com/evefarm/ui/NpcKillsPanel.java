package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

public final class NpcKillsPanel extends JPanel {

    private final OfficersPanel officersPanel;
    private final KillsPanel killsPanel;

    public NpcKillsPanel(AppContext appContext) {
        super(new BorderLayout());
        officersPanel = new OfficersPanel(appContext);
        killsPanel = new KillsPanel(appContext);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Officers", Icons.TARGET, officersPanel);
        tabs.addTab("All Kills", Icons.CROSSHAIR, killsPanel);
        add(tabs, BorderLayout.CENTER);
    }

    public void onShown() {
        officersPanel.onShown();
        killsPanel.onShown();
    }

    public void refreshCharacterFilter() {
        killsPanel.refreshCharacterFilter();
        officersPanel.onShown();
    }
}
