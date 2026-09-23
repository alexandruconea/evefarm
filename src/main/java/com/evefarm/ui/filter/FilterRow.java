package com.evefarm.ui.filter;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.FlowLayout;
import java.util.List;

final class FilterRow extends JPanel {

    private final JCheckBox enabledBox = new JCheckBox();
    private final JComboBox<FilterCondition.LogicOp> logicCombo =
            new JComboBox<>(FilterCondition.LogicOp.values());
    private final JComboBox<String> columnCombo;
    private final JComboBox<FilterCondition.Operator> operatorCombo =
            new JComboBox<>(FilterCondition.Operator.values());
    private final JTextField valueField = new JTextField(14);
    private final JButton addButton = new JButton("+");
    private final JButton removeButton = new JButton("-");

    FilterRow(List<String> columnNames, Runnable onChange, Runnable onAdd, Runnable onRemove) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 2));

        String[] columns = new String[columnNames.size() + 1];
        columns[0] = FilterCondition.ALL_COLUMNS;
        for (int i = 0; i < columnNames.size(); i++) {
            columns[i + 1] = columnNames.get(i);
        }
        columnCombo = new JComboBox<>(columns);

        enabledBox.setSelected(true);
        add(enabledBox);
        add(logicCombo);
        add(columnCombo);
        add(operatorCombo);
        add(valueField);
        add(com.evefarm.ui.ButtonSizing.row(2, addButton, removeButton));

        enabledBox.addActionListener(e -> onChange.run());
        logicCombo.addActionListener(e -> onChange.run());
        columnCombo.addActionListener(e -> onChange.run());
        operatorCombo.addActionListener(e -> onChange.run());
        valueField.getDocument().addDocumentListener((SimpleDocumentListener) onChange::run);
        addButton.addActionListener(e -> onAdd.run());
        removeButton.addActionListener(e -> onRemove.run());
    }

    void setFirstRow(boolean first) {
        logicCombo.setEnabled(!first);
    }

    void setRemoveEnabled(boolean enabled) {
        removeButton.setEnabled(enabled);
    }

    FilterCondition toCondition() {
        FilterCondition condition = new FilterCondition();
        condition.setEnabled(enabledBox.isSelected());
        condition.setLogicOp((FilterCondition.LogicOp) logicCombo.getSelectedItem());
        condition.setColumn((String) columnCombo.getSelectedItem());
        condition.setOperator((FilterCondition.Operator) operatorCombo.getSelectedItem());
        condition.setValue(valueField.getText());
        return condition;
    }

    void applyCondition(FilterCondition condition) {
        enabledBox.setSelected(condition.isEnabled());
        logicCombo.setSelectedItem(condition.getLogicOp());
        columnCombo.setSelectedItem(condition.getColumn());
        operatorCombo.setSelectedItem(condition.getOperator());
        valueField.setText(condition.getValue());
    }

    @FunctionalInterface
    private interface SimpleDocumentListener extends DocumentListener {
        void update();

        @Override
        default void insertUpdate(DocumentEvent e) {
            update();
        }

        @Override
        default void removeUpdate(DocumentEvent e) {
            update();
        }

        @Override
        default void changedUpdate(DocumentEvent e) {
            update();
        }
    }
}
