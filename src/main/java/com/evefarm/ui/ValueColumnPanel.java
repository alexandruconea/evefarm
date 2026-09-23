package com.evefarm.ui;

import com.evefarm.model.ValueSummary;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

final class ValueColumnPanel extends JPanel {

    private final JPanel rows = new JPanel(new GridBagLayout());

    ValueColumnPanel() {
        super(new BorderLayout());
        add(rows, BorderLayout.NORTH);
    }

    void setSummary(String title, ValueSummary summary) {
        rows.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        rows.add(titleBar(title), c);
        c.gridy++;
        rows.add(row("Total", summary == null ? 0 : summary.total()), c);
        c.gridy++;
        rows.add(row("Wallet Balance", summary == null ? 0 : summary.walletBalance()), c);
        c.gridy++;
        rows.add(row("Assets", summary == null ? 0 : summary.assetsValue()), c);
        c.gridy++;
        rows.add(row("Sell Orders", summary == null ? 0 : summary.sellOrdersValue()), c);
        c.gridy++;
        rows.add(escrowRow(summary), c);
        c.gridy++;
        rows.add(bestRow("Best Asset", summary == null ? null : summary.bestAssetName(),
                summary == null ? 0 : summary.bestAssetValue()), c);
        c.gridy++;
        rows.add(bestRow("Best Ship", summary == null ? null : summary.bestShipName(),
                summary == null ? 0 : summary.bestShipValue()), c);
        c.gridy++;
        rows.add(bestRow("Best Module", summary == null ? null : summary.bestModuleName(),
                summary == null ? 0 : summary.bestModuleValue()), c);

        revalidate();
        repaint();
    }

    private static Color titleColor() {
        return FlatLaf.isLafDark() ? new Color(34, 34, 34) : new Color(55, 71, 79);
    }

    private static Color barColor() {
        return FlatLaf.isLafDark() ? new Color(60, 60, 60) : new Color(96, 125, 139);
    }

    private JLabel titleBar(String title) {
        JLabel label = new JLabel(title);
        label.setOpaque(true);
        label.setBackground(titleColor());
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() + 2f));
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        return label;
    }

    private JPanel row(String label, double value) {
        return row(label, value > 0 ? IskFormatter.format(value) + " isk" : null);
    }

    private JPanel escrowRow(ValueSummary summary) {
        if (summary == null || (summary.escrowValue() <= 0 && summary.escrowToCoverValue() <= 0)) {
            return row("Escrows (To Cover)", (String) null);
        }
        String text = IskFormatter.format(summary.escrowValue()) + " isk";
        if (summary.escrowToCoverValue() > 0) {
            text += " (" + IskFormatter.format(summary.escrowToCoverValue()) + " isk)";
        }
        return row("Escrows (To Cover)", text);
    }

    private JPanel bestRow(String label, String itemName, double value) {
        if (itemName == null || value <= 0) {
            return row(label, (String) null);
        }
        return row(label, itemName + "\n" + IskFormatter.format(value) + " isk");
    }

    private JPanel row(String label, String value) {
        JLabel heading = new JLabel(label);
        heading.setOpaque(true);
        heading.setBackground(barColor());
        heading.setForeground(Color.WHITE);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD));
        heading.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JLabel valueLabel;
        if (value == null) {
            valueLabel = new JLabel("none");
            valueLabel.setFont(valueLabel.getFont().deriveFont(Font.ITALIC));
            valueLabel.setForeground(Color.GRAY);
        } else {
            valueLabel = new JLabel("<html>" + value.replace("\n", "<br>") + "</html>");
        }
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setBorder(BorderFactory.createEmptyBorder(3, 8, 6, 8));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(heading, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }
}
