package com.evefarm.ui;

import com.evefarm.AppContext;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;

public final class NpcKillsPanel extends JPanel {

    private final OfficersPanel officersPanel;
    private final SpawnsPanel spawnsPanel;
    private final KillsPanel killsPanel;

    public NpcKillsPanel(AppContext appContext) {
        super(new BorderLayout());
        officersPanel = new OfficersPanel(appContext);
        spawnsPanel = new SpawnsPanel(appContext);
        killsPanel = new KillsPanel(appContext);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Officers", Icons.TARGET, officersPanel);
        tabs.addTab("Spawns", Icons.NOTEBOOK, spawnsPanel);
        tabs.addTab("All Kills", Icons.CROSSHAIR, killsPanel);
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedComponent() == spawnsPanel) {
                spawnsPanel.onShown();
            }
        });
        add(tabs, BorderLayout.CENTER);
        appContext.killService.addLogChangeListener(() -> SwingUtilities.invokeLater(this::refreshAfterScan));
    }

    private void refreshAfterScan() {
        if (officersPanel.isShowing()) {
            officersPanel.onShown();
        }
        if (spawnsPanel.isShowing()) {
            spawnsPanel.onShown();
        }
        if (killsPanel.isShowing()) {
            killsPanel.refreshKills();
        }
    }

    public void onShown() {
        officersPanel.onShown();
        spawnsPanel.onShown();
        killsPanel.onShown();
    }

    public void refreshCharacterFilter() {
        killsPanel.refreshCharacterFilter();
        officersPanel.onShown();
        spawnsPanel.onShown();
    }
}
