package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.LoyaltyPointHistoryRow;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.Text;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;

final class LoyaltyPointHistoryDialog extends JDialog {

    private static final String ALL_CHARACTERS = "All characters";

    private static final List<ColumnDef<LoyaltyPointHistoryRow>> COLUMNS = List.of(
            new ColumnDef<>("date", "Date", String.class, r -> DateUtil.formatIsoInstant(r.recordedAt()),
                    "When EVE Farm saw this LP, in your local time"),
            new ColumnDef<>("character", "Character", EveEntity.class,
                    r -> EveEntity.character(r.characterName(), r.characterId()),
                    "The character who holds the LP"),
            new ColumnDef<>("corporation", "Corporation", String.class, LoyaltyPointHistoryRow::corporationName,
                    "The corporation the LP is with"),
            new ColumnDef<>("lp", "LP", String.class, r -> Text.count(r.loyaltyPoints()),
                    "The LP the character had with the corporation"),
            new ColumnDef<>("change", "Change", String.class, r -> formatChange(r.change()),
                    "How much the LP went up or down since the line before"));

    private final AppContext appContext;
    private final ColumnTableModel<LoyaltyPointHistoryRow> tableModel = new ColumnTableModel<>(COLUMNS);
    private final JTable table = new JTable(tableModel);
    private TableRowSorter<ColumnTableModel<LoyaltyPointHistoryRow>> sorter;
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JLabel countLabel = new JLabel("Loading...");

    private LoyaltyPointHistoryDialog(Window owner, AppContext appContext) {
        super(owner, "LP History", ModalityType.MODELESS);
        this.appContext = appContext;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        load();
    }

    static void open(Window owner, AppContext appContext) {
        new LoyaltyPointHistoryDialog(owner, appContext).setVisible(true);
    }

    static String formatChange(Long change) {
        if (change == null) {
            return "";
        }
        return (change > 0 ? "+" : "") + Text.count(change);
    }

    private void buildUi() {
        sorter = SortableTables.withWidths(table, tableModel, "lpHistory", appContext.tableColumnStateDao);
        EveEntityCells.install(table, appContext.itemIconService);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(720, 380));

        characterCombo.addActionListener(e -> applyFilter());
        ComboSizing.names(characterCombo);
        ComboFilter.install(characterCombo);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(new JLabel("Character:"));
        top.add(characterCombo);

        JButton close = new JButton("Close", Icons.CANCEL);
        close.addActionListener(e -> dispose());
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(countLabel, BorderLayout.CENTER);
        bottom.add(close, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(top, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
    }

    private void load() {
        BackgroundLoader.load(appContext.loyaltyPointService::getHistory, this::show, "the LP history",
                failure -> countLabel.setText("Couldn't load the LP history - see the log"));
    }

    private void show(List<LoyaltyPointHistoryRow> rows) {
        tableModel.setRows(rows);
        characterCombo.removeAllItems();
        characterCombo.addItem(ALL_CHARACTERS);
        rows.stream().map(LoyaltyPointHistoryRow::characterName).distinct().sorted()
                .forEach(characterCombo::addItem);
        TableStyler.packColumns(table);
        applyFilter();
    }

    private void applyFilter() {
        Object selected = characterCombo.getSelectedItem();
        sorter.setRowFilter(selected == null || ALL_CHARACTERS.equals(selected) ? null
                : new RowFilter<>() {
                    @Override
                    public boolean include(Entry<? extends ColumnTableModel<LoyaltyPointHistoryRow>, ? extends Integer> entry) {
                        return selected.equals(tableModel.rowAt(entry.getIdentifier()).characterName());
                    }
                });
        countLabel.setText(table.getRowCount() == 0
                ? "No LP changes saved yet. They are saved each time LP is updated."
                : table.getRowCount() + " changes");
    }
}
