package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.StandingRow;
import com.evefarm.service.StandingService;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class StandingsPanel extends JPanel {

    private static final String FACTIONS_KEY = "standingsFactions";
    private static final String AGENTS_KEY = "standingsAgents";
    private static final double DEFAULT_DIVIDER = 0.45;
    private static final int INDENT = 16;

    private final AppContext appContext;
    private final StandingsTableModel factionModel = StandingsTableModel.factionsAndCorporations();
    private final StandingsTableModel agentModel = StandingsTableModel.agents();
    private final JTable factionTable = new JTable();
    private final JTable agentTable = new JTable();
    private final JPanel factionFilters = new JPanel();
    private final JPanel agentFilters = new JPanel();
    private final JLabel factionCount = new JLabel("0 standings");
    private final JLabel agentCount = new JLabel("0 agents");
    private final JLabel agentTitle = new JLabel("Agents");
    private final JButton showAllAgentsButton = new JButton("Show All Agents");
    private final JLabel hintLabel = new JLabel(" ");
    private final JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private volatile Set<StandingRow> indented = Set.of();
    private volatile StandingRow selected;
    private DataTablePanelSupport<StandingRow> factionSupport;
    private DataTablePanelSupport<StandingRow> agentSupport;

    public StandingsPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        postInit();
    }

    private void postInit() {
        factionSupport = new DataTablePanelSupport<>(appContext, FACTIONS_KEY, factionModel, factionTable,
                factionFilters, factionCount, "standings", this::loadFactionsAndCorporations);
        factionSupport.setOnRowsShown(this::updateHint);
        agentSupport = new DataTablePanelSupport<>(appContext, AGENTS_KEY, agentModel, agentTable, agentFilters,
                agentCount, "agents", this::loadAgents);
        factionSupport.init();
        agentSupport.init();
        installColors(factionTable, factionModel, true);
        installColors(agentTable, agentModel, false);

        factionTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectionChanged();
            }
        });
        installShowAllAgents();
        DividerMemory.install(split, appContext.settingsDao, SettingsDao.STANDINGS_DIVIDER, DEFAULT_DIVIDER);
    }

    public void onShown() {
        factionTable.getRowSorter().setSortKeys(null);
        factionSupport.reload();
        agentSupport.reload();
    }

    public void refreshCharacterFilter() {
        onShown();
    }

    private List<StandingRow> loadFactionsAndCorporations() {
        List<StandingRow> rows = appContext.standingService.getRows();
        indented = StandingService.corporationsUnderTheirFaction(rows);
        return StandingService.factionsAndCorporations(rows);
    }

    private List<StandingRow> loadAgents() {
        return StandingService.agents(appContext.standingService.getRows(), selected);
    }

    private void selectionChanged() {
        int viewRow = factionTable.getSelectedRow();
        StandingRow row = viewRow < 0 ? null : factionModel.rowAt(factionTable.convertRowIndexToModel(viewRow));
        if (Objects.equals(row, selected)) {
            return;
        }
        selected = row;
        agentTitle.setText(row == null ? "Agents" : "Agents of " + row.name() + " - " + row.characterName());
        showAllAgentsButton.setVisible(row != null);
        agentSupport.reload();
    }

    private void installShowAllAgents() {
        showAllAgentsButton.putClientProperty("JButton.buttonType", "toolBarButton");
        showAllAgentsButton.setVisible(false);
        showAllAgentsButton.addActionListener(e -> factionTable.clearSelection());
        factionTable.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "showAllAgents");
        factionTable.getActionMap().put("showAllAgents", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                factionTable.clearSelection();
            }
        });
        factionTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (factionTable.rowAtPoint(e.getPoint()) < 0) {
                    factionTable.clearSelection();
                }
            }
        });
        factionTable.setToolTipText("Click a faction or corporation to see its agents on the right");
    }

    private void updateHint() {
        hintLabel.setText(factionModel.getRowCount() == 0
                ? "No standings saved yet. Run Update > Standings. A character added before standings were "
                + "supported must be added again (File > Characters...)."
                : " ");
    }

    private void installColors(JTable table, StandingsTableModel model, boolean indentCorporations) {
        for (Class<?> type : List.of(Object.class, Integer.class, Long.class)) {
            TableCellRenderer defaultRenderer = table.getDefaultRenderer(type);
            table.setDefaultRenderer(type, (tbl, value, isSelected, hasFocus, row, column) -> {
                Component c = defaultRenderer.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row,
                        column);
                StandingRow standing = model.rowAt(tbl.convertRowIndexToModel(row));
                String key = model.columns().get(tbl.convertColumnIndexToModel(column)).key();
                if (!isSelected) {
                    Color color = "standing".equals(key) ? colorFor(standing.standing()) : null;
                    c.setForeground(color == null ? tbl.getForeground() : color);
                }
                if (indentCorporations && "name".equals(key) && indented.contains(standing)
                        && c instanceof JComponent component) {
                    component.setBorder(BorderFactory.createCompoundBorder(component.getBorder(),
                            BorderFactory.createEmptyBorder(0, INDENT, 0, 0)));
                }
                return c;
            });
        }
    }

    static Color colorFor(double standing) {
        boolean dark = FlatLaf.isLafDark();
        if (standing > 0) {
            return dark ? new Color(110, 170, 255) : new Color(21, 101, 192);
        }
        if (standing < 0) {
            return dark ? new Color(255, 110, 110) : new Color(198, 40, 40);
        }
        return null;
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.setBorder(BorderFactory.createEmptyBorder(8, 4, 0, 8));
        top.add(hintLabel);

        JLabel factionTitle = new JLabel("Factions and corporations");
        JPanel agentHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        agentHeader.add(agentTitle);
        agentHeader.add(showAllAgentsButton);

        split.setLeftComponent(side(factionTitle, factionFilters, factionTable, factionCount));
        split.setRightComponent(side(agentHeader, agentFilters, agentTable, agentCount));
        split.setResizeWeight(DEFAULT_DIVIDER);
        split.setBorder(BorderFactory.createEmptyBorder());
        bold(factionTitle);
        bold(agentTitle);

        add(top, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
    }

    private static JPanel side(JComponent title, JPanel filters, JTable table, JLabel count) {
        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.add(title, BorderLayout.NORTH);
        header.add(filters, BorderLayout.CENTER);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(count, BorderLayout.SOUTH);
        return panel;
    }

    private static void bold(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
    }
}
