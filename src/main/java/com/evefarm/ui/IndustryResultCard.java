package com.evefarm.ui;

import com.evefarm.service.BuildPlanner;
import com.evefarm.service.IndustryService;
import com.evefarm.util.IskFormatter;
import com.formdev.flatlaf.FlatLaf;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class IndustryResultCard {

    record Line(String label, String value, String detail, Color color, boolean strong) {

        Line(String label, String value, String detail) {
            this(label, value, detail, null, false);
        }
    }

    private final CardPanel card = new CardPanel(new GridBagLayout());

    IndustryResultCard() {
        card.add(UiColors.mutedLabel("Choose a blueprint to see what it costs and what it earns."));
    }

    JComponent component() {
        return card;
    }

    void show(IndustryService.Result result, IndustryService.Option option) {
        card.removeAll();
        int row = 0;
        Line makes = new Line("Makes", String.format(Locale.US, "%,d × %s", option.manufacturing().units(),
                result.productName()), null);
        addRow(row++, makes, false, false);
        for (List<Line> group : groups(result, option)) {
            for (int i = 0; i < group.size(); i++) {
                addRow(row++, group.get(i), true, i == 0);
            }
        }
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridy = row;
        filler.weighty = 1;
        card.add(Box.createVerticalGlue(), filler);
        card.revalidate();
        card.repaint();
    }

    static List<List<Line>> groups(IndustryService.Result result, IndustryService.Option option) {
        List<Line> selling = List.of(
                new Line("Sells for", IskFormatter.format(option.sale().grossValue()), each(result.productPrice())),
                new Line("Sales tax and broker", IskFormatter.format(option.sale().fees()), null));

        BuildPlanner.Plan plan = option.plan();
        List<Line> costs = new ArrayList<>();
        long built = plan.components().stream().filter(BuildPlanner.Component::built).count();
        costs.add(new Line("Materials", IskFormatter.format(plan.keptCost()),
                built > 0 ? String.format(Locale.US, "%d item%s built", built, built == 1 ? "" : "s") : null));
        if (plan.surplusValue() > 0) {
            costs.add(new Line("Surplus", IskFormatter.format(plan.surplusCost()), surplusDetail(plan)));
        }
        costs.add(new Line("Job fees", IskFormatter.format(option.manufacturing().jobFee()), null));
        if (option.invention() != null) {
            costs.add(new Line("Invention", IskFormatter.format(option.inventionCost()),
                    String.format(Locale.US, "%.1f%% chance, %s%s", option.invention().chance() * 100,
                            option.relic() == null ? "" : option.relic().name() + ", ",
                            option.decryptor() == null ? "no decryptor" : option.decryptor().name())));
        }
        costs.add(new Line("Total cost", IskFormatter.format(option.totalCost()), each(option.unitCost()), null,
                true));
        double fromStock = option.stocked().stockValue();
        String spending = option.invention() == null ? "What is left to buy and the job fees"
                : "What is left to buy, the job fees and the invention";
        costs.add(new Line("To spend", IskFormatter.format(option.toSpend()),
                fromStock > 0 ? spending + ". " + IskFormatter.format(fromStock) + " is already in your stock"
                        : spending));

        List<Line> earnings = List.of(
                new Line("Profit", IskFormatter.format(option.profit()), each(option.unitProfit()),
                        profitColor(option.profit()), true),
                new Line("ISK per hour", IskFormatter.format(option.iskPerHour()), null,
                        profitColor(option.iskPerHour()), false));

        List<Line> time = new ArrayList<>();
        time.add(new Line("Build time", PlanTableModel.formatDuration(option.manufacturing().time()),
                jobs(option.manufacturing().jobs(), result.skills().lines())));
        if (!plan.extraTime().isZero()) {
            time.add(new Line("Component time", PlanTableModel.formatDuration(plan.extraTime()),
                    "The reactions and components, built before the final job"));
        }
        return List.of(selling, costs, earnings, time);
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

    private void addRow(int row, Line line, boolean ruled, boolean startsGroup) {
        JLabel label = new Rigid(line.label(), SwingConstants.LEFT);
        label.setForeground(UiColors.muted());
        JLabel value = new Squeezable(line.value(), SwingConstants.RIGHT);
        if (line.color() != null) {
            value.setForeground(line.color());
        }
        if (line.strong()) {
            label.setFont(label.getFont().deriveFont(Font.BOLD));
            label.setForeground(UIManager.getColor("Label.foreground"));
            value.setFont(value.getFont().deriveFont(Font.BOLD));
        }
        if (!ruled) {
            value.setFont(value.getFont().deriveFont(Font.BOLD));
        }
        label.setToolTipText(line.detail());
        value.setToolTipText(line.detail());

        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setOpaque(false);
        Border padding = BorderFactory.createEmptyBorder(5, 0, 5, 0);
        if (ruled) {
            padding = BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, faintLine()), padding);
        }
        if (startsGroup) {
            padding = BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0), padding);
        }
        panel.setBorder(padding);
        panel.add(label, BorderLayout.WEST);
        panel.add(value, BorderLayout.CENTER);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        card.add(panel, c);
    }

    static Color faintLine() {
        Color line = UIManager.getColor("Separator.foreground");
        Color background = UIManager.getColor("Table.background");
        if (line == null || background == null) {
            return new Color(0xE6E6E6);
        }
        return new Color((line.getRed() + background.getRed()) / 2, (line.getGreen() + background.getGreen()) / 2,
                (line.getBlue() + background.getBlue()) / 2);
    }

    private static final class Rigid extends JLabel {

        Rigid(String text, int alignment) {
            super(text, alignment);
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }
    }

    private static final class Squeezable extends JLabel {

        Squeezable(String text, int alignment) {
            super(text, alignment);
            putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, getPreferredSize().height);
        }
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
