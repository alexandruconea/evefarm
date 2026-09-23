package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.ItemType;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.util.List;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

final class AddDropDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(AddDropDialog.class.getName());

    record Result(ItemType item, int quantity, double unitPrice) {
    }

    private final AppContext appContext;
    private final String officerName;
    private final JTextField searchField = new JTextField(28);
    private final DefaultListModel<ItemType> itemModel = new DefaultListModel<>();
    private final JList<ItemType> itemList = new JList<>(itemModel);
    private final JLabel listLabel = new JLabel(" ");
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JTextField priceField = new JTextField(16);
    private final JLabel priceHint = new JLabel(" ");
    private final JButton addButton = new JButton("Add", Icons.ADD);
    private final AtomicInteger searchGeneration = new AtomicInteger();
    private final AtomicInteger priceGeneration = new AtomicInteger();
    private final Timer searchDebounce;
    private Result result;

    private AddDropDialog(Window owner, AppContext appContext, String officerName) {
        super(owner, "Add Drop - " + officerName, ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.officerName = officerName;
        this.searchDebounce = new Timer(250, e -> runSearch());
        searchDebounce.setRepeats(false);
        buildUi();
        runSearch();
        pack();
        setLocationRelativeTo(owner);
    }

    static Result showDialog(Window owner, AppContext appContext, String officerName) {
        AddDropDialog dialog = new AddDropDialog(owner, appContext, officerName);
        dialog.setVisible(true);
        return dialog.result;
    }

    private void buildUi() {
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                searchDebounce.restart();
            }
        });
        itemList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        itemList.setVisibleRowCount(14);
        itemList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onItemSelected(itemList.getSelectedValue());
            }
        });
        JScrollPane listScroll = new JScrollPane(itemList);
        listScroll.setPreferredSize(new Dimension(460, 300));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        searchRow.add(new JLabel("Search:"));
        searchRow.add(searchField);
        top.add(searchRow, BorderLayout.NORTH);
        top.add(listLabel, BorderLayout.SOUTH);

        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        form.add(new JLabel("Quantity:"));
        form.add(quantitySpinner);
        form.add(new JLabel("Unit price (ISK):"));
        form.add(priceField);

        addButton.setEnabled(false);
        addButton.addActionListener(e -> confirm());
        JButton cancelButton = new JButton("Cancel", Icons.CANCEL);
        cancelButton.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.add(ButtonSizing.row(6, addButton, cancelButton));

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(form, BorderLayout.NORTH);
        bottom.add(priceHint, BorderLayout.CENTER);
        bottom.add(buttons, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(top, BorderLayout.NORTH);
        content.add(listScroll, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(addButton);
    }

    private void runSearch() {
        String text = searchField.getText().trim();
        int generation = searchGeneration.incrementAndGet();
        new SwingWorker<List<ItemType>, Void>() {
            @Override
            protected List<ItemType> doInBackground() {
                return text.isEmpty()
                        ? appContext.officerService.suggestedDrops(officerName)
                        : appContext.officerService.searchItems(text);
            }

            @Override
            protected void done() {
                if (generation != searchGeneration.get()) {
                    return;
                }
                try {
                    List<ItemType> items = get();
                    itemModel.clear();
                    items.forEach(itemModel::addElement);
                    if (items.isEmpty()) {
                        listLabel.setText(text.isEmpty()
                                ? "The item list isn't downloaded yet - run Scan Gamelogs once (needs internet)."
                                : "No items match \"" + text + "\".");
                    } else {
                        listLabel.setText(text.isEmpty()
                                ? officerName + "'s modules - type to search every item"
                                : items.size() + " matching items (officer modules first)");
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Item search failed", e);
                }
            }
        }.execute();
    }

    private void onItemSelected(ItemType item) {
        addButton.setEnabled(item != null);
        if (item == null) {
            return;
        }
        int generation = priceGeneration.incrementAndGet();
        priceHint.setText("Looking up the market price...");
        new SwingWorker<OptionalDouble, Void>() {
            @Override
            protected OptionalDouble doInBackground() {
                return appContext.officerService.currentPrice(item.typeId());
            }

            @Override
            protected void done() {
                if (generation != priceGeneration.get()) {
                    return;
                }
                try {
                    OptionalDouble price = get();
                    if (price.isPresent()) {
                        priceField.setText(IskFormatter.formatPlain(price.getAsDouble()));
                        priceHint.setText("Market price from your price provider - overwrite it with a sale price if you like.");
                    } else {
                        priceField.setText("");
                        priceHint.setText("No market price for this item - type one in.");
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Price lookup failed for " + item.typeName(), e);
                    priceHint.setText("Couldn't look up a price - type one in.");
                }
            }
        }.execute();
    }

    private void confirm() {
        ItemType item = itemList.getSelectedValue();
        if (item == null) {
            return;
        }
        Double price = parsePrice(priceField.getText());
        if (price == null) {
            JOptionPane.showMessageDialog(this, "Enter a unit price in ISK (e.g. 1,250,000,000).",
                    "Add Drop", JOptionPane.WARNING_MESSAGE);
            return;
        }
        result = new Result(item, (Integer) quantitySpinner.getValue(), price);
        dispose();
    }

    static Double parsePrice(String text) {
        String cleaned = text == null ? "" : text.replaceAll("[,\\s\\u00a0]|ISK", "");
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(cleaned);
            return value < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
