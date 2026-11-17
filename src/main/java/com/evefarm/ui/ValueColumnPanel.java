package com.evefarm.ui;

import com.evefarm.model.ValueSummary;
import com.evefarm.model.ValueSummary.Today;
import com.evefarm.util.IskFormatter;
import com.evefarm.util.Text;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ValueColumnPanel extends JPanel {

    private final JLabel titleLabel = new JLabel(" ");
    private final CardPanel card = new CardPanel(new GridBagLayout());

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

    void setSummary(String title, ValueSummary summary) {
        titleLabel.setText(title);
        card.removeAll();
        int row = 0;
        addRow(row++, "Total", isk(summary == null ? 0 : summary.total()), true);
        addRow(row++, "Wallet Balance", isk(summary == null ? 0 : summary.walletBalance()), false);
        addRow(row++, "Assets", isk(summary == null ? 0 : summary.assetsValue()), false);
        addRow(row++, "Sell Orders", isk(summary == null ? 0 : summary.sellOrdersValue()), false);
        addRow(row++, "Earned Today", today(summary == null ? null : summary.today()), false,
                todayTip(summary == null ? null : summary.today()));
        addRow(row++, "Best Asset", best(summary == null ? null : summary.bestAssetName(),
                summary == null ? 0 : summary.bestAssetValue()), false);
        addRow(row++, "Best Ship", best(summary == null ? null : summary.bestShipName(),
                summary == null ? 0 : summary.bestShipValue()), false);
        addRow(row, "Best Module", best(summary == null ? null : summary.bestModuleName(),
                summary == null ? 0 : summary.bestModuleValue()), false);
        revalidate();
        repaint();
    }

    private void addRow(int row, String label, String value, boolean emphasized) {
        addRow(row, label, value, emphasized, null);
    }

    private void addRow(int row, String label, String value, boolean emphasized, String tip) {
        JLabel name = new JLabel(label);
        name.setForeground(UiColors.muted());
        JLabel amount = new JLabel(value == null ? "—" : value);
        amount.setHorizontalAlignment(SwingConstants.RIGHT);
        if (value == null) {
            amount.setForeground(UiColors.muted());
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
        name.setToolTipText(tip);
        amount.setToolTipText(tip);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        card.add(line, c);
    }

    private static String isk(double value) {
        return value > 0 ? IskFormatter.format(value) : null;
    }

    private static String today(Today today) {
        if (today == null || today.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        if (today.isk() != 0) {
            parts.add(IskFormatter.format(today.isk()));
        }
        if (today.lp() != 0) {
            parts.add(Text.count(today.lp()) + " LP");
        }
        String earned = String.join(" + ", parts);
        if (today.lpValue() == 0) {
            return earned;
        }
        return "<html><div style='text-align:right'>" + Text.escapeHtml(earned) + "<br>"
                + IskFormatter.format(today.total()) + "</div></html>";
    }

    private static String todayTip(Today today) {
        String tip = "ISK from missions and the LP earned today, as in LP Store > Report";
        if (today != null && today.targetIskPerLp() > 0) {
            tip += ". The total counts the LP at " + String.format(Locale.US, "%,.0f ISK/LP", today.targetIskPerLp())
                    + ", your target set with Set ISK/LP Target... in Offers";
        }
        return tip;
    }

    private static String best(String itemName, double value) {
        if (itemName == null || value <= 0) {
            return null;
        }
        return "<html><div style='text-align:right'>" + Text.escapeHtml(itemName) + "<br>"
                + IskFormatter.format(value) + "</div></html>";
    }
}
