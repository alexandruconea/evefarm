package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.esi.EsiException;
import com.evefarm.model.ContractItem;
import com.evefarm.model.ContractRow;
import com.evefarm.service.ItemIconService;
import com.evefarm.ui.column.ColumnDef;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.util.DateUtil;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class ContractContentsDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(ContractContentsDialog.class.getName());

    private final AppContext appContext;
    private final ContractRow contract;
    private final ColumnTableModel<ContractItem> includedModel;
    private final ColumnTableModel<ContractItem> askedModel;
    private final JTable includedTable;
    private final JTable askedTable;
    private final JLabel includedTotal = new JLabel(" ");
    private final JLabel askedTotal = new JLabel(" ");
    private final JLabel status = new JLabel("Loading the contract's items from EVE...");
    private final JPanel askedSection = new JPanel(new BorderLayout(0, 4));

    ContractContentsDialog(Window owner, AppContext appContext, ContractRow contract) {
        super(owner, "Contract - " + describe(contract), ModalityType.MODELESS);
        this.appContext = appContext;
        this.contract = contract;
        this.includedModel = itemsModel(appContext.itemIconService);
        this.askedModel = itemsModel(appContext.itemIconService);
        this.includedTable = new JTable(includedModel);
        this.askedTable = new JTable(askedModel);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        pack();
        setLocationRelativeTo(owner);
        loadItems();
    }

    private static String describe(ContractRow contract) {
        if (contract.title() != null && !contract.title().isBlank()) {
            return contract.title();
        }
        return Text.titleCase(contract.type());
    }

    private void buildUi() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        content.add(details());
        content.add(section("Items in this contract", includedTable, includedTotal));
        askedSection.add(sectionTitle("Items asked for in return"), BorderLayout.NORTH);
        askedSection.add(scroll(askedTable), BorderLayout.CENTER);
        askedSection.add(askedTotal, BorderLayout.SOUTH);
        askedSection.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        askedSection.setVisible(false);
        content.add(askedSection);
        status.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(status);

        JButton close = new JButton("Close", Icons.CANCEL);
        close.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(close);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private JPanel details() {
        List<String[]> fields = new ArrayList<>();
        addField(fields, "Character", contract.characterName());
        addField(fields, "Type", Text.titleCase(contract.type()));
        addField(fields, "Status", Text.titleCase(contract.status()));
        addField(fields, "Issuer", contract.issuerName());
        addField(fields, "Assignee", contract.assigneeName());
        addField(fields, "Acceptor", contract.acceptorName());
        addField(fields, "Price", isk(contract.price()));
        addField(fields, "Reward", isk(contract.reward()));
        addField(fields, "Collateral", isk(contract.collateral()));
        addField(fields, "Volume", contract.volume() == null || contract.volume() == 0 ? null
                : String.format(Locale.US, "%,.2f m3", contract.volume()));
        addField(fields, "From", contract.startLocationName());
        addField(fields, "To", contract.endLocationName() != null
                && !contract.endLocationName().equals(contract.startLocationName())
                ? contract.endLocationName() : null);
        addField(fields, "Issued", DateUtil.formatIsoInstant(contract.dateIssued()));
        addField(fields, "Expires", DateUtil.formatIsoInstant(contract.dateExpired()));
        addField(fields, "Completed", DateUtil.formatIsoInstant(contract.dateCompleted()));

        JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 12);
        int half = (fields.size() + 1) / 2;
        for (int i = 0; i < fields.size(); i++) {
            c.gridx = i < half ? 0 : 2;
            c.gridy = i < half ? i : i - half;
            JLabel label = new JLabel(fields.get(i)[0] + ":");
            label.setForeground(UiColors.muted());
            panel.add(label, c);
            c.gridx++;
            JLabel value = new JLabel(fields.get(i)[1]);
            value.putClientProperty("html.disable", Boolean.TRUE);
            c.insets = new Insets(2, 0, 2, 28);
            panel.add(value, c);
            c.insets = new Insets(2, 0, 2, 12);
        }
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 4;
        filler.weightx = 1;
        panel.add(Box.createHorizontalGlue(), filler);
        return panel;
    }

    private static void addField(List<String[]> fields, String label, String value) {
        if (value != null && !value.isBlank()) {
            fields.add(new String[]{label, value});
        }
    }

    private static String isk(Double value) {
        return value == null || value == 0 ? null : IskFormatter.format(value);
    }

    private JPanel section(String title, JTable table, JLabel total) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(sectionTitle(title), BorderLayout.NORTH);
        panel.add(scroll(table), BorderLayout.CENTER);
        panel.add(total, BorderLayout.SOUTH);
        return panel;
    }

    private static JLabel sectionTitle(String title) {
        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private JScrollPane scroll(JTable table) {
        TableStyler.style(table);
        table.setRowHeight(Math.max(table.getRowHeight(), ItemIconService.RENDER_SIZE + 6));
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, 200));
        return scroll;
    }

    private void loadItems() {
        new SwingWorker<List<ContractItem>, Void>() {
            @Override
            protected List<ContractItem> doInBackground() {
                return appContext.contractService.listContractItems(contract.characterId(), contract.contractId());
            }

            @Override
            protected void done() {
                try {
                    showItems(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Failed to load the items of contract " + contract.contractId(), cause);
                    status.setText(cause instanceof EsiException esi
                            && (esi.statusCode() == 403 || esi.statusCode() == 404)
                            ? "EVE doesn't show the items of this contract any more - it may have expired or "
                            + "been deleted."
                            : "Couldn't load the items from EVE: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void showItems(List<ContractItem> items) {
        List<ContractItem> included = items.stream().filter(ContractItem::included).toList();
        List<ContractItem> asked = items.stream().filter(item -> !item.included()).toList();
        includedModel.setRows(included);
        askedModel.setRows(asked);
        includedTotal.setText(total(included));
        askedTotal.setText(total(asked));
        askedSection.setVisible(!asked.isEmpty());
        TableStyler.packColumns(includedTable);
        TableStyler.packColumns(askedTable);
        for (ContractItem item : items) {
            appContext.itemIconService.loadAsync(item.typeId(), () -> {
                includedTable.repaint();
                askedTable.repaint();
            });
        }
        status.setText(items.isEmpty() ? "EVE returned no items for this contract." : " ");
        pack();
    }

    private static String total(List<ContractItem> items) {
        if (items.isEmpty()) {
            return "No items.";
        }
        double total = items.stream().filter(item -> item.totalValue() != null)
                .mapToDouble(ContractItem::totalValue).sum();
        long unpriced = items.stream().filter(item -> item.totalValue() == null).count();
        return "Estimated value: " + IskFormatter.format(total)
                + (unpriced == 0 ? "" : "  (" + unpriced + " without a market price)");
    }

    private static ColumnTableModel<ContractItem> itemsModel(ItemIconService icons) {
        return new ColumnTableModel<>(List.of(
                new ColumnDef<>("icon", "", ImageIcon.class, item -> icons.iconOrPlaceholder(item.typeId()),
                        "The item's icon"),
                new ColumnDef<>("item", "Item", String.class, ContractItem::name, "The item's name"),
                new ColumnDef<>("group", "Group", String.class,
                        item -> item.groupName() == null ? "" : item.groupName(),
                        "The item's group, such as Frigate or Mineral"),
                new ColumnDef<>("quantity", "Quantity", String.class, item -> Text.count(item.quantity()),
                        "How many units"),
                new ColumnDef<>("unitPrice", "Unit Price", String.class,
                        item -> item.unitPrice() == null ? "" : IskFormatter.format(item.unitPrice()),
                        "The price of one unit, from your price provider"),
                new ColumnDef<>("total", "Total", String.class,
                        item -> item.totalValue() == null ? "" : IskFormatter.format(item.totalValue()),
                        "The unit price times the quantity")));
    }
}
