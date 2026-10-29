package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.MiningRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.service.MiningService;
import com.evefarm.service.MiningStats;
import com.evefarm.ui.column.ColumnDescriptions;
import com.evefarm.ui.column.ColumnWidthMemory;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

public final class MiningPanel extends TabPanel {

    private static final String PANEL_KEY = "mining";
    private static final double DEFAULT_REFINE_RATE = 90.6;

    private final AppContext appContext;
    private final MiningTableModel tableModel;
    private final JTable table = new JTable() {
        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row < 0 || column < 0 || !"refinedInto".equals(getColumnModel().getColumn(column).getIdentifier())) {
                return super.getToolTipText(event);
            }
            return MiningTableModel.refinedIntoToolTip(tableModel.rowAt(convertRowIndexToModel(row)).materials());
        }
    };
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
        updateRefinedControls();

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
        ColumnWidthMemory.install(statsTable, "miningStats", appContext.tableColumnStateDao);
    }

    @Override
    public void onShown() {
        support.reload();
    }

    @Override
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
        updateRefinedControls();
        support.reload();
    }

    private void readChoices() {
        period = selectedPeriod();
        valuation = selectedValuation();
        refineRate = ((Number) rateSpinner.getValue()).doubleValue();
    }

    private void updateRefinedControls() {
        boolean refined = selectedValuation() == MiningService.Valuation.REFINED;
        rateLabel.setEnabled(refined);
        rateSpinner.setEnabled(refined);
        boolean materialListed = ((DefaultComboBoxModel<?>) groupByCombo.getModel())
                .getIndexOf(MiningStats.GroupBy.MATERIAL) >= 0;
        if (refined && !materialListed) {
            groupByCombo.addItem(MiningStats.GroupBy.MATERIAL);
        } else if (!refined && materialListed) {
            if (selectedGroupBy() == MiningStats.GroupBy.MATERIAL) {
                groupByCombo.setSelectedItem(MiningStats.GroupBy.ORE);
            }
            groupByCombo.removeItem(MiningStats.GroupBy.MATERIAL);
        }
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
        support.visibleRows().stream().map(MiningRow::iconTypeId).distinct()
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
        ComboSizing.options(periodCombo);
        controls.add(periodCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(new JLabel("Value:"));
        ComboSizing.options(valuationCombo);
        controls.add(valuationCombo);
        controls.add(Box.createHorizontalStrut(8));
        controls.add(rateLabel);
        ComboSizing.fix(rateSpinner, 70);
        controls.add(rateSpinner);

        JPanel ledgerTop = new JPanel(new BorderLayout(0, 6));
        ledgerTop.add(controls, BorderLayout.NORTH);
        ledgerTop.add(filterBarContainer, BorderLayout.CENTER);
        JPanel ledgerPanel = new JPanel(new BorderLayout());
        ledgerPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
        ledgerPanel.add(ledgerTop, BorderLayout.NORTH);
        ledgerPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        ledgerPanel.add(countLabel, BorderLayout.SOUTH);

        add(StatisticsPane.below(ledgerPanel, overallLabel, groupByCombo, statsTable), BorderLayout.CENTER);
    }

    private static final class StatsTableModel extends AbstractTableModel implements ColumnDescriptions {

        private static final String[] COLUMNS = {"Group", "Quantity", "Volume", "Value", "Days", "Value per Day",
                "Share"};
        private static final String[] DESCRIPTIONS = {
                "The day, character, ore, kind, system or refined material, as chosen in Group by",
                "How many units, when grouped by ore or material",
                "The volume mined, in m3 - for a material, the volume of the refined material",
                "What the ore mined is worth", "On how many days there was mining", "The value divided by the days",
                "The group's share of the total value"};

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
        public String columnDescription(int column) {
            return DESCRIPTIONS[column];
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 4 ? Integer.class : String.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            MiningStats.Summary summary = summaries.get(row);
            return switch (column) {
                case 0 -> summary.label();
                case 1 -> summary.quantity() == null ? "" : Text.count(Math.round(summary.quantity()));
                case 2 -> MiningTableModel.formatVolume(summary.volume());
                case 3 -> IskFormatter.format(summary.value());
                case 4 -> summary.days();
                case 5 -> IskFormatter.format(summary.valuePerDay());
                default -> total <= 0 ? "" : String.format(Locale.US, "%.1f%%", summary.value() * 100 / total);
            };
        }
    }
}
