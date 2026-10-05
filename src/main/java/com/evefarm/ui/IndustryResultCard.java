package com.evefarm.ui;

import com.evefarm.service.BuildPlanner;
import com.evefarm.service.IndustryService;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class IndustryResultCard {

    private record Line(String label, String value, String detail, Color color, boolean strong) {
    }

    private final CardPanel card = new CardPanel(new GridBagLayout());

    IndustryResultCard() {
        card.add(UiColors.mutedLabel("Choose a blueprint to see what it costs and what it earns."));
    }

    JComponent component() {
        return card;
    }

    void show(IndustryService.Result result, IndustryService.Option option) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Sells for", IskFormatter.format(option.sale().grossValue()),
                each(result.productPrice()), null, false));
        lines.add(new Line("Sales tax and broker", IskFormatter.format(option.sale().fees()), null, null, false));
        long built = option.plan().components().stream().filter(BuildPlanner.Component::built).count();
        BuildPlanner.Plan plan = option.plan();
        lines.add(new Line("Materials", IskFormatter.format(plan.keptCost()),
                built > 0 ? String.format(Locale.US, "%d item%s built", built, built == 1 ? "" : "s") : null, null,
                false));
        if (plan.surplusValue() > 0) {
            lines.add(new Line("Surplus", IskFormatter.format(plan.surplusCost()), surplusDetail(plan), null,
                    false));
        }
        lines.add(new Line("Job fee", IskFormatter.format(option.manufacturing().jobFee()), null, null, false));
        if (option.invention() != null) {
            lines.add(new Line("Invention", IskFormatter.format(option.inventionCost()),
                    String.format(Locale.US, "%.1f%% chance, %s", option.invention().chance() * 100,
                            option.decryptor() == null ? "no decryptor" : option.decryptor().name()), null, false));
        }
        lines.add(new Line("Total cost", IskFormatter.format(option.totalCost()), each(option.unitCost()), null,
                false));
        lines.add(new Line("Profit", IskFormatter.format(option.profit()), each(option.unitProfit()),
                profitColor(option.profit()), true));
        lines.add(new Line("Build time", PlanTableModel.formatDuration(option.manufacturing().time()),
                jobs(option.manufacturing().jobs(), result.skills().lines()), null, false));
        if (!option.plan().extraTime().isZero()) {
            lines.add(new Line("Component time", PlanTableModel.formatDuration(option.plan().extraTime()),
                    "reactions and components, before the build", null, false));
        }
        lines.add(new Line("ISK per hour", IskFormatter.format(option.iskPerHour()), null,
                profitColor(option.iskPerHour()), false));
        card.removeAll();
        card.add(UiColors.mutedLabel("Makes"), cell(0, 0, 1, GridBagConstraints.WEST));
        card.add(plainText(String.format(Locale.US, "%,d × %s", option.manufacturing().units(),
                result.productName()), SwingConstants.LEFT), cell(1, 0, 2, GridBagConstraints.WEST));
        for (int i = 0; i < lines.size(); i++) {
            addLine(i + 1, lines.get(i));
        }
        GridBagConstraints filler = cell(0, lines.size() + 1, 3, GridBagConstraints.WEST);
        filler.weighty = 1;
        card.add(Box.createVerticalGlue(), filler);
        card.revalidate();
        card.repaint();
    }

    private static String surplusDetail(BuildPlanner.Plan plan) {
        String worth = IskFormatter.format(plan.surplusValue());
        return switch (plan.surplus()) {
            case KEEP -> "worth " + worth + ", kept for later builds";
            case SELL -> "worth " + worth + ", sold for "
                    + IskFormatter.format(plan.surplusValue() - plan.surplusCost());
            case WASTE -> "worth " + worth + ", counted as waste";
        };
    }

    private static String jobs(int jobs, int lines) {
        return jobs > 1 ? String.format(Locale.US, "%d jobs, %d at a time", jobs, Math.min(jobs, lines)) : null;
    }

    private static String each(double value) {
        return IskFormatter.format(value) + " each";
    }

    private void addLine(int row, Line line) {
        JLabel label = UiColors.mutedLabel(line.label());
        JLabel value = plainText(line.value(), SwingConstants.RIGHT);
        if (line.color() != null) {
            value.setForeground(line.color());
        }
        if (line.strong()) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            value.setFont(value.getFont().deriveFont(Font.BOLD));
        }
        JLabel detail = plainText(line.detail() == null ? "" : line.detail(), SwingConstants.LEFT);
        detail.setForeground(UiColors.muted());
        card.add(label, cell(0, row, 1, GridBagConstraints.WEST));
        GridBagConstraints valueCell = cell(1, row, 1, GridBagConstraints.EAST);
        valueCell.ipadx = 6;
        card.add(value, valueCell);
        GridBagConstraints detailCell = cell(2, row, 1, GridBagConstraints.WEST);
        detailCell.weightx = 1;
        card.add(detail, detailCell);
    }

    private static JLabel plainText(String text, int alignment) {
        JLabel label = new JLabel(text, alignment);
        label.putClientProperty("html.disable", Boolean.TRUE);
        return label;
    }

    private static GridBagConstraints cell(int column, int row, int width, int anchor) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.gridwidth = width;
        c.anchor = anchor;
        c.insets = new Insets(3, 0, 3, column == 2 ? 0 : 18);
        return c;
    }

    private static Color profitColor(double value) {
        if (value > 0) {
            return FlatLaf.isLafDark() ? new Color(110, 200, 120) : new Color(46, 125, 50);
        }
        if (value < 0) {
            Color red = UIManager.getColor("Actions.Red");
            return red != null ? red : new Color(0xDB5860);
        }
        return null;
    }
}
