package com.evefarm.ui;

import com.evefarm.model.LpPurchase;
import com.evefarm.util.IskFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Consumer;

final class LpPurchaseDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LpPurchase base;
    private final Consumer<LpPurchase> onSave;
    private final double unitsPerOffer;
    private final double lpPerOffer;
    private final double iskPerOffer;
    private final JSpinner offersSpinner;
    private final JTextField dateField = new JTextField(14);
    private final JSpinner otherCostSpinner;
    private final JSpinner targetSpinner;
    private final JSpinner usedSpinner;
    private final JTextField notesField = new JTextField(28);
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel errorLabel = new JLabel(" ");

    LpPurchaseDialog(Window owner, String title, LpPurchase base, Consumer<LpPurchase> onSave) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        this.base = base;
        this.onSave = onSave;
        int offers = Math.max(1, base.offers());
        unitsPerOffer = (double) base.units() / offers;
        lpPerOffer = (double) base.lpSpent() / offers;
        iskPerOffer = base.iskSpent() / offers;
        offersSpinner = new JSpinner(new SpinnerNumberModel(offers, 1, Math.max(offers, 1_000_000), 1));
        otherCostSpinner = new JSpinner(new SpinnerNumberModel(Math.clamp(base.otherCost() / offers, 0.0, 1e15),
                0.0, 1e15, 1_000.0));
        targetSpinner = new JSpinner(new SpinnerNumberModel(
                base.targetIskPerLp() == null ? 0.0 : Math.clamp(base.targetIskPerLp(), 0.0, 1e9), 0.0, 1e9, 50.0));
        usedSpinner = new JSpinner(new SpinnerNumberModel(base.usedUnits(), 0, Long.MAX_VALUE, 1));
        dateField.setText(DATE_TIME.format(base.purchasedAt().atZone(ZoneId.systemDefault())));
        notesField.setText(base.notes() == null ? "" : base.notes());
        buildUi();
        updateSummary();
        offersSpinner.addChangeListener(e -> updateSummary());
        otherCostSpinner.addChangeListener(e -> updateSummary());
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JLabel item = new JLabel(base.typeName());
        item.putClientProperty("html.disable", Boolean.TRUE);
        item.setFont(item.getFont().deriveFont(Font.BOLD, item.getFont().getSize2D() + 1f));
        JLabel where = UiColors.mutedLabel((base.characterName() == null ? "" : base.characterName() + " - ")
                + base.corporationName());
        where.putClientProperty("html.disable", Boolean.TRUE);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 6, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 10);
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        form.add(item, c);
        c.gridy++;
        form.add(where, c);
        c.gridwidth = 1;
        addRow(form, c, "Times bought:", offersSpinner, "How many times you bought the offer");
        addRow(form, c, "Bought on:", dateField, "When you bought it, as year-month-day hour:minute in your local "
                + "time");
        addRow(form, c, "Other cost per offer:", otherCostSpinner, "What the other items the offer asks for and "
                + "any build materials cost you, for one offer");
        addRow(form, c, "Target ISK/LP:", targetSpinner, "The ISK/LP you want from this purchase, counted like "
                + "the LP Store tab. The Target Price is the sell price that makes it. Set it to 0 to have no target");
        addRow(form, c, "Units used:", usedSpinner, "Units you used yourself instead of selling them");
        addRow(form, c, "Notes:", notesField, "Anything you want to remember about this purchase");
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        form.add(summaryLabel, c);
        c.gridy++;
        errorLabel.setForeground(UiColors.danger());
        form.add(errorLabel, c);

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ButtonSizing.row(6, save, cancel));
        getRootPane().setDefaultButton(save);

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private static void addRow(JPanel form, GridBagConstraints c, String label, Component field,
                               String tip) {
        c.gridy++;
        c.gridx = 0;
        JLabel text = new JLabel(label);
        text.setToolTipText(tip);
        form.add(text, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        if (field instanceof JComponent component) {
            component.setToolTipText(tip);
        }
        form.add(field, c);
        c.fill = GridBagConstraints.NONE;
    }

    private int offers() {
        return ((Number) offersSpinner.getValue()).intValue();
    }

    private void updateSummary() {
        int offers = offers();
        double other = ((Number) otherCostSpinner.getValue()).doubleValue() * offers;
        summaryLabel.setText(String.format(Locale.US, "%,d units for %,d LP and %s",
                Math.round(unitsPerOffer * offers), Math.round(lpPerOffer * offers),
                IskFormatter.format(iskPerOffer * offers + other)));
    }

    private void save() {
        LocalDateTime boughtOn;
        try {
            boughtOn = LocalDateTime.parse(dateField.getText().trim(), DATE_TIME);
        } catch (DateTimeParseException e) {
            errorLabel.setText("Write the date as year-month-day hour:minute, for example 2026-10-09 14:30");
            pack();
            return;
        }
        int offers = offers();
        long units = Math.round(unitsPerOffer * offers);
        long used = Math.min(units, ((Number) usedSpinner.getValue()).longValue());
        double target = ((Number) targetSpinner.getValue()).doubleValue();
        String notes = notesField.getText().strip();
        LpPurchase purchase = new LpPurchase(base.purchaseId(), base.characterId(), base.characterName(),
                base.corporationId(), base.corporationName(), base.offerId(), base.typeId(), base.typeName(), offers,
                units, Math.round(lpPerOffer * offers), iskPerOffer * offers,
                ((Number) otherCostSpinner.getValue()).doubleValue() * offers, target > 0 ? target : null,
                boughtOn.atZone(ZoneId.systemDefault()).toInstant(), base.journalEntryId(), used,
                notes.isEmpty() ? null : notes);
        dispose();
        onSave.accept(purchase);
    }
}
