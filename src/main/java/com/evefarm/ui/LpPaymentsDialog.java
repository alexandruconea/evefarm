package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.LpPurchase;
import com.evefarm.service.LpPurchaseService.Candidate;
import com.evefarm.service.LpPurchaseService.PendingPayment;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LpPaymentsDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(LpPaymentsDialog.class.getName());

    private final AppContext appContext;
    private final Runnable onChanged;
    private final LpPaymentsTableModel model = new LpPaymentsTableModel();
    private final JTable table = new JTable(model);
    private final DefaultComboBoxModel<Candidate> offerModel = new DefaultComboBoxModel<>();
    private final JComboBox<Candidate> offerCombo = new JComboBox<>(offerModel);
    private final JTextField findField = new JTextField(16);
    private final JButton recordButton = new JButton("Record...", Icons.ADD);
    private final JButton ignoreButton = new JButton("Ignore", Icons.CANCEL);
    private final JLabel statusLabel = new JLabel("Looking up the offers of each store...");
    private List<PendingPayment> payments = new ArrayList<>();

    LpPaymentsDialog(Window owner, AppContext appContext, Runnable onChanged) {
        super(owner, "LP Store Payments", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.onChanged = onChanged;
        buildUi();
        load();
        setSize(960, 480);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showCandidates();
            }
        });
        TableStyler.style(table);
        offerCombo.setPreferredSize(new Dimension(300, offerCombo.getPreferredSize().height));
        offerCombo.setToolTipText("The offer you bought with this payment, and how many times. Listed and sold "
                + "count the units of the item put up for sale and sold in the 30 days after the payment, in assets "
                + "what you have now. The offers that fit them best come first");
        findField.putClientProperty("JTextField.placeholderText", "Find an offer");
        findField.setToolTipText("Show only the offers whose name has this text");
        findField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showCandidates();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                showCandidates();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                showCandidates();
            }
        });
        recordButton.setToolTipText("Record the purchase with the chosen offer");
        recordButton.addActionListener(e -> record());
        ignoreButton.setToolTipText("This payment needs no record. It won't be shown again");
        ignoreButton.addActionListener(e -> ignore());
        JButton close = new JButton("Close", Icons.CANCEL);
        close.addActionListener(e -> dispose());

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        statusLabel.setToolTipText("Payments to LP stores from your wallet journal that are not recorded yet. "
                + "Run Update > Journal to see the newest ones");
        top.add(statusLabel, BorderLayout.WEST);

        JPanel choose = new JPanel(new GridBagLayout());
        choose.setBorder(BorderFactory.createEmptyBorder(6, 10, 8, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 0, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        choose.add(new JLabel("Offer:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        choose.add(offerCombo, c);
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        choose.add(new JLabel("Find:"), c);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(findField);
        JPanel buttons = ButtonSizing.row(6, recordButton, ignoreButton);
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        actions.add(buttons);
        JPanel row = new JPanel(new BorderLayout());
        row.add(actions, BorderLayout.WEST);
        row.add(close, BorderLayout.EAST);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        choose.add(row, c);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10),
                scroll.getBorder()));
        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(choose, BorderLayout.SOUTH);
        showCandidates();
    }

    private void load() {
        new SwingWorker<List<PendingPayment>, Void>() {
            @Override
            protected List<PendingPayment> doInBackground() {
                return appContext.lpPurchaseService.pendingPayments();
            }

            @Override
            protected void done() {
                try {
                    payments = new ArrayList<>(get());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Couldn't load the LP Store payments", e);
                    statusLabel.setText("Couldn't look up the payments: " + e.getMessage());
                    return;
                }
                showPayments();
            }
        }.execute();
    }

    private void showPayments() {
        model.setRows(List.copyOf(payments));
        TableStyler.packColumns(table);
        statusLabel.setText(payments.isEmpty() ? "Every LP Store payment is recorded."
                : payments.size() + (payments.size() == 1 ? " payment" : " payments")
                        + " not recorded yet. Pick one, check the offer and click Record.");
        if (!payments.isEmpty()) {
            table.setRowSelectionInterval(0, 0);
        }
        showCandidates();
    }

    private PendingPayment selected() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 ? null : model.rowAt(table.convertRowIndexToModel(viewRow));
    }

    private void showCandidates() {
        PendingPayment payment = selected();
        String find = findField.getText().strip().toLowerCase(Locale.ROOT);
        offerModel.removeAllElements();
        if (payment != null) {
            payment.candidates().stream()
                    .filter(candidate -> candidate.label().toLowerCase(Locale.ROOT).contains(find))
                    .forEach(offerModel::addElement);
        }
        offerCombo.setEnabled(offerModel.getSize() > 0);
        recordButton.setEnabled(payment != null && offerModel.getSize() > 0);
        ignoreButton.setEnabled(payment != null);
    }

    private void record() {
        PendingPayment payment = selected();
        Candidate candidate = (Candidate) offerCombo.getSelectedItem();
        if (payment == null || candidate == null) {
            return;
        }
        LpPurchase base = appContext.lpPurchaseService.purchaseFrom(candidate.offer(), candidate.times(),
                payment.payment().characterId(), payment.payment().corporationId(), payment.corporationName(),
                payment.payment().date(), payment.payment().entryId());
        new LpPurchaseDialog(this, "Record Purchase", base, purchase -> {
            appContext.lpPurchaseService.record(purchase);
            done(payment);
        }).setVisible(true);
    }

    private void ignore() {
        PendingPayment payment = selected();
        if (payment == null) {
            return;
        }
        appContext.lpPurchaseService.ignorePayment(payment.payment().entryId());
        done(payment);
    }

    private void done(PendingPayment payment) {
        payments.remove(payment);
        showPayments();
        onChanged.run();
    }
}
