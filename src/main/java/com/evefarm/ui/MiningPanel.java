package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.MiningRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.service.MiningService;
import com.evefarm.service.MiningStats;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Locale;

public final class MiningPanel extends JPanel {

    private static final String PANEL_KEY = "mining";
    private static final double DEFAULT_REFINE_RATE = 90.6;

    private final AppContext appContext;
    private final MiningTableModel tableModel;
    private final JTable table = new JTable();
    private final JPanel filterBarContainer = new JPanel();
    private final JLabel countLabel = new JLabel("0 entries");
    private final JComboBox<MiningService.Period> periodCombo = new JComboBox<>(MiningService.Period.values());
    private final JComboBox<MiningService.Valuation> valuationCombo =
            new JComboBox<>(MiningService.Valuation.values());
    private final JSpinner rateSpinner = new JSpinner(new SpinnerNumberModel(DEFAULT_REFINE_RATE, 0.0, 100.0, 0.1));
    private final JLabel rateLabel = new JLabel("Refining yield (%):");
    private final JLabel overallLabel = new JLabel(" ");
    private final JComboBox<MiningStats.GroupBy> groupByCombo = new JComboBox<>(MiningStats.GroupBy.values());
    private final StatsTableModel statsModel = new StatsTableModel();
    private final JTable statsTable = new JTable(statsModel);
    private volatile MiningService.Period period = MiningService.Period.MONTH;
    private volatile MiningService.Valuation valuation = MiningService.Valuation.ORE;
    private volatile double refineRate = DEFAULT_REFINE_RATE;
    private DataTablePanelSupport<MiningRow> support;

    public MiningPanel(AppContext appContext) {
        this.appContext = appContext;
        this.tableModel = new MiningTableModel(appContext.itemIconService);
        buildUi();
        postInit();
    }

    private void postInit() {
        periodCombo.setSelectedItem(MiningService.Period.MONTH);
        appContext.settingsDao.getEnum(SettingsDao.MINING_PERIOD, MiningService.Period.class)
                .ifPresent(periodCombo::setSelectedItem);
        appContext.settingsDao.getEnum(SettingsDao.MINING_VALUATION, MiningService.Valuation.class)
                .ifPresent(valuationCombo::setSelectedItem);
        appContext.settingsDao.getEnum(SettingsDao.MINING_GROUP_BY, MiningStats.GroupBy.class)
                .ifPresent(groupByCombo::setSelectedItem);
        rateSpinner.setValue(savedRefineRate());
        readChoices();
        updateRateEnabled();

        periodCombo.addActionListener(e -> choiceChanged(SettingsDao.MINING_PERIOD, selectedPeriod().name()));
        valuationCombo.addActionListener(e -> choiceChanged(SettingsDao.MINING_VALUATION, selectedValuation().name()));
        rateSpinner.addChangeListener(e -> choiceChanged(SettingsDao.MINING_REFINE_RATE,
                String.valueOf(((Number) rateSpinner.getValue()).doubleValue())));
        groupByCombo.addActionListener(e -> {
            appContext.settingsDao.set(SettingsDao.MINING_GROUP_BY, selectedGroupBy().name());
            refreshStats();
        });
        valuationCombo.setToolTipText("Ore price: what the ore sells for. Compressed ore price: what the same ore "
                + "sells for compressed. Refined minerals: what the minerals from reprocessing it sell for.");
        rateSpinner.setToolTipText("Your reprocessing yield, used for the refined minerals value");

        support = new DataTablePanelSupport<>(appContext, PANEL_KEY, tableModel, table, filterBarContainer,
                countLabel, "entries", () -> appContext.miningService.getRows(period, valuation, refineRate / 100.0));
        support.setOnRowsShown(() -> {
            refreshStats();
            loadIcons();
        });
        support.init();
        table.setRowHeight(Math.max(table.getRowHeight(), ItemIconService.RENDER_SIZE + 6));
        TableStyler.style(statsTable);
    }

    public void onShown() {
        support.reload();
    }

    public void refreshCharacterFilter() {
        support.reload();
    }

    private double savedRefineRate() {
        double rate = appContext.settingsDao.getDouble(SettingsDao.MINING_REFINE_RATE, DEFAULT_REFINE_RATE);
        return Math.clamp(rate, 0, 100);
    }

    private void choiceChanged(String key, String value) {
        appContext.settingsDao.set(key, value);
        readChoices();
        updateRateEnabled();
        support.reload();
    }

    private void readChoices() {
        period = selectedPeriod();
        valuation = selectedValuation();
        refineRate = ((Number) rateSpinner.getValue()).doubleValue();
    }

    private void updateRateEnabled() {
        boolean refined = selectedValuation() == MiningService.Valuation.REFINED;
        rateLabel.setEnabled(refined);
        rateSpinner.setEnabled(refined);
    }

    private MiningService.Period selectedPeriod() {
        Object selected = periodCombo.getSelectedItem();
        return selected instanceof MiningService.Period chosen ? chosen : MiningService.Period.MONTH;
    }

    private MiningService.Valuation selectedValuation() {
        Object selected = valuationCombo.getSelectedItem();
        return selected instanceof MiningService.Valuation chosen ? chosen : MiningService.Valuation.ORE;
    }

    private MiningStats.GroupBy selectedGroupBy() {
        Object selected = groupByCombo.getSelectedItem();
        return selected instanceof MiningStats.GroupBy chosen ? chosen : MiningStats.GroupBy.DAY;
    }

    private void loadIcons() {
        support.visibleRows().stream().map(MiningRow::typeId).distinct()
                .forEach(typeId -> appContext.itemIconService.loadAsync(typeId, table::repaint));
    }

    private void refreshStats() {
        List<MiningRow> rows = support.visibleRows();
        if (rows.isEmpty()) {
            overallLabel.setText(tableModel.getRowCount() == 0
                    ? "No mining saved yet. Run Update > Mining Ledger. A character added before mining was "
                    + "supported must be added again (File > Characters...) to allow it."
                    : "No mining matches the filter.");
        } else {
            overallLabel.setText(summary(MiningStats.overall(rows)));
        }
        statsModel.setSummaries(MiningStats.grouped(rows, selectedGroupBy()));
        TableStyler.packColumns(statsTable);
    }

    static String summary(MiningStats.Summary overall) {
        return MiningTableModel.formatVolume(overall.volume()) + "   |   " + IskFormatter.format(overall.value())
                + "   |   " + overall.days() + (overall.days() == 1 ? " day" : " days") + " mined"
                + "   |   " + IskFormatter.format(overall.valuePerDay()) + " per day";
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 8));
        controls.add(new JLabel("Show:"));
        controls.add(periodCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(new JLabel("Value:"));
        controls.add(valuationCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(rateLabel);
        rateSpinner.setPreferredSize(new Dimension(70, rateSpinner.getPreferredSize().height));
        controls.add(rateSpinner);

        JPanel ledgerTop = new JPanel(new BorderLayout(0, 6));
        ledgerTop.add(controls, BorderLayout.NORTH);
        ledgerTop.add(filterBarContainer, BorderLayout.CENTER);
        JPanel ledgerPanel = new JPanel(new BorderLayout());
        ledgerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        ledgerPanel.add(ledgerTop, BorderLayout.NORTH);
        ledgerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        ledgerPanel.add(countLabel, BorderLayout.SOUTH);

        JPanel statsHeader = new JPanel(new BorderLayout(12, 0));
        JLabel statsTitle = new JLabel("Statistics");
        statsTitle.setFont(statsTitle.getFont().deriveFont(Font.BOLD));
        JPanel groupBy = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        groupBy.add(new JLabel("Group by:"));
        groupBy.add(groupByCombo);
        statsHeader.add(statsTitle, BorderLayout.WEST);
        statsHeader.add(overallLabel, BorderLayout.CENTER);
        statsHeader.add(groupBy, BorderLayout.EAST);
        JPanel statsPanel = new JPanel(new BorderLayout(0, 6));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        statsPanel.add(statsHeader, BorderLayout.NORTH);
        JScrollPane statsScroll = new JScrollPane(statsTable);
        statsScroll.setPreferredSize(new Dimension(600, 150));
        statsPanel.add(statsScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ledgerPanel, statsPanel);
        split.setResizeWeight(0.7);
        split.setBorder(BorderFactory.createEmptyBorder());
        add(split, BorderLayout.CENTER);
    }

    private static final class StatsTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Group", "Volume", "Value", "Days", "Value per Day", "Share"};

        private List<MiningStats.Summary> summaries = List.of();
        private double total;

        void setSummaries(List<MiningStats.Summary> summaries) {
            this.summaries = summaries;
            this.total = summaries.stream().mapToDouble(MiningStats.Summary::value).sum();
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return summaries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 3 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            MiningStats.Summary summary = summaries.get(row);
            return switch (column) {
                case 0 -> summary.label();
                case 1 -> MiningTableModel.formatVolume(summary.volume());
                case 2 -> IskFormatter.format(summary.value());
                case 3 -> summary.days();
                case 4 -> IskFormatter.format(summary.valuePerDay());
                default -> total <= 0 ? "" : String.format(Locale.US, "%.1f%%", summary.value() * 100 / total);
            };
        }
    }
}
