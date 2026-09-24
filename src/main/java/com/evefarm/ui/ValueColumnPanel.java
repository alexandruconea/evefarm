package com.evefarm.ui;

import com.evefarm.model.ValueSummary;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.ui.FlatLineBorder;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

final class ValueColumnPanel extends JPanel {

    private final JLabel titleLabel = new JLabel(" ");
    private final JPanel card = new JPanel(new GridBagLayout());

    ValueColumnPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 3f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        add(titleLabel, BorderLayout.NORTH);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(card, BorderLayout.NORTH);
        add(holder, BorderLayout.CENTER);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (card != null) {
            styleCard();
        }
    }

    void setSummary(String title, ValueSummary summary) {
        titleLabel.setText(title);
        styleCard();
        card.removeAll();
        int row = 0;
        addRow(row++, "Total", isk(summary == null ? 0 : summary.total()), true);
        addRow(row++, "Wallet Balance", isk(summary == null ? 0 : summary.walletBalance()), false);
        addRow(row++, "Assets", isk(summary == null ? 0 : summary.assetsValue()), false);
        addRow(row++, "Sell Orders", isk(summary == null ? 0 : summary.sellOrdersValue()), false);
        addRow(row++, "Escrows (To Cover)", escrow(summary), false);
        addRow(row++, "Best Asset", best(summary == null ? null : summary.bestAssetName(),
                summary == null ? 0 : summary.bestAssetValue()), false);
        addRow(row++, "Best Ship", best(summary == null ? null : summary.bestShipName(),
                summary == null ? 0 : summary.bestShipValue()), false);
        addRow(row, "Best Module", best(summary == null ? null : summary.bestModuleName(),
                summary == null ? 0 : summary.bestModuleValue()), false);
        revalidate();
        repaint();
    }

    private void styleCard() {
        card.setBackground(UIManager.getColor("Table.background"));
        Color border = UIManager.getColor("Component.borderColor");
        card.setBorder(new FlatLineBorder(new Insets(4, 14, 4, 14), border == null ? Color.LIGHT_GRAY : border, 1, 12));
    }

    private void addRow(int row, String label, String value, boolean emphasized) {
        JLabel name = new JLabel(label);
        name.setForeground(mutedColor());
        JLabel amount = new JLabel(value == null ? "—" : value);
        amount.setHorizontalAlignment(SwingConstants.RIGHT);
        if (value == null) {
            amount.setForeground(mutedColor());
        }
        if (emphasized) {
            name.setFont(name.getFont().deriveFont(Font.BOLD));
            amount.setFont(amount.getFont().deriveFont(Font.BOLD, amount.getFont().getSize2D() + 2f));
        }

        Color separator = UIManager.getColor("Separator.foreground");
        int top = row == 0 ? 0 : 1;
        JPanel line = new JPanel(new BorderLayout(16, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(top, 0, 0, 0, separator == null ? Color.LIGHT_GRAY : separator),
                BorderFactory.createEmptyBorder(9, 0, 9, 0)));
        line.add(name, BorderLayout.WEST);
        line.add(amount, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        card.add(line, c);
    }

    private static Color mutedColor() {
        Color themed = UIManager.getColor("Label.disabledForeground");
        return themed != null ? themed : Color.GRAY;
    }

    private static String isk(double value) {
        return value > 0 ? IskFormatter.format(value) : null;
    }

    private static String escrow(ValueSummary summary) {
        if (summary == null || (summary.escrowValue() <= 0 && summary.escrowToCoverValue() <= 0)) {
            return null;
        }
        String text = IskFormatter.format(summary.escrowValue());
        if (summary.escrowToCoverValue() > 0) {
            text += " (" + IskFormatter.format(summary.escrowToCoverValue()) + ")";
        }
        return text;
    }

    private static String best(String itemName, double value) {
        if (itemName == null || value <= 0) {
            return null;
        }
        return "<html><div style='text-align:right'>" + escape(itemName) + "<br>"
                + IskFormatter.format(value) + "</div></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
