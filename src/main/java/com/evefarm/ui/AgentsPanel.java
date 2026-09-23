package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.AgentRow;
import com.evefarm.util.DateUtil;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AgentsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(AgentsPanel.class.getName());
    private static final String PANEL_KEY = "agents";

    private final AppContext appContext;
    private final AgentsTableModel tableModel = new AgentsTableModel();
    private final JButton refreshButton = new JButton("Refresh Agent Data...");
    private final JLabel statusLabel = new JLabel();
    private final JPanel filterBarContainer = new JPanel();
    private final JTable table = new JTable();
    private final JLabel countLabel = new JLabel("0 agents");
    private DataTablePanelSupport<AgentRow> support;

    public AgentsPanel(AppContext appContext) {
        this.appContext = appContext;
        initComponents();
        postInit();
    }

    private void postInit() {
        refreshButton.addActionListener(e -> refreshAgentData());
        updateStatusLabel();

        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                countLabel, "agents", appContext.agentDao::listAll);
        support.init();
    }

    public void onShown() {
        support.reload();
    }

    private void refreshAgentData() {
        refreshButton.setEnabled(false);
        refreshButton.setText("Importing...");
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                return appContext.agentImportService.importAgents();
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("Refresh Agent Data...");
                try {
                    int count = get();
                    updateStatusLabel();
                    support.reload();
                    JOptionPane.showMessageDialog(AgentsPanel.this,
                            "Imported " + count + " agents.",
                            "Refresh Agent Data", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to import agent data", e);
                    JOptionPane.showMessageDialog(AgentsPanel.this,
                            "Failed to import agent data: " + e.getCause(),
                            "Refresh Agent Data", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void updateStatusLabel() {
        String lastImported = appContext.settingsDao.get(SettingsDao.AGENTS_LAST_IMPORTED_AT).orElse(null);
        statusLabel.setText(lastImported == null
                ? "Never imported yet - click Refresh Agent Data to load the catalog."
                : "Last imported: " + DateUtil.formatIsoInstant(lastImported));
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        toolbar.add(refreshButton, BorderLayout.WEST);
        toolbar.add(statusLabel, BorderLayout.CENTER);
        add(toolbar, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        centerPanel.add(filterBarContainer, BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        centerPanel.add(countLabel, BorderLayout.SOUTH);
        add(centerPanel, BorderLayout.CENTER);
    }
}
