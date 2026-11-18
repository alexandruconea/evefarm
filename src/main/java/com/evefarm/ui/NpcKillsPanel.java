package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.ui.PhosphorIcons.Hue;

import javax.swing.SwingUtilities;

public final class NpcKillsPanel extends SubTabs {

    private final OfficersPanel officersPanel;
    private final SpawnsPanel spawnsPanel;
    private final KillsPanel killsPanel;

    public NpcKillsPanel(AppContext appContext) {
        this(appContext, new OfficersPanel(appContext), new SpawnsPanel(appContext), new KillsPanel(appContext));
        appContext.killService.addLogChangeListener(() -> SwingUtilities.invokeLater(this::refreshAfterScan));
    }

    private NpcKillsPanel(AppContext appContext, OfficersPanel officersPanel, SpawnsPanel spawnsPanel,
                          KillsPanel killsPanel) {
        super(appContext.settingsDao, "kills",
                new Page("officers", "Officers", PhosphorIcons.menu("crown", Hue.RED), officersPanel),
                new Page("spawns", "Spawns", PhosphorIcons.menu("users-three", Hue.RED), spawnsPanel),
                new Page("allKills", "All Kills", PhosphorIcons.menu("skull", Hue.RED), killsPanel));
        this.officersPanel = officersPanel;
        this.spawnsPanel = spawnsPanel;
        this.killsPanel = killsPanel;
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
}
