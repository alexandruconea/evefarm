package com.evefarm.ui;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

final class StatisticsPane {

    static JSplitPane below(JComponent top, JLabel overallLabel, JComboBox<?> groupByCombo, JTable statsTable) {
        JLabel statsTitle = new JLabel("Statistics");
        statsTitle.setFont(statsTitle.getFont().deriveFont(Font.BOLD));
        JPanel groupBy = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        groupBy.add(new JLabel("Group by:"));
        ComboSizing.options(groupByCombo);
        groupBy.add(groupByCombo);
        JPanel statsHeader = new JPanel(new BorderLayout(12, 0));
        statsHeader.add(statsTitle, BorderLayout.WEST);
        statsHeader.add(overallLabel, BorderLayout.CENTER);
        statsHeader.add(groupBy, BorderLayout.EAST);
        JPanel statsPanel = new JPanel(new BorderLayout(0, 6));
        statsPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        statsPanel.add(statsHeader, BorderLayout.NORTH);
        JScrollPane statsScroll = new JScrollPane(statsTable);
        statsScroll.setPreferredSize(new Dimension(600, 150));
        statsPanel.add(statsScroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, statsPanel);
        split.setResizeWeight(0.7);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private StatisticsPane() {
    }
}
