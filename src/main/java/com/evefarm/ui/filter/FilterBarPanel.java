package com.evefarm.ui.filter;

import com.evefarm.db.dao.SavedFilterDao;
import com.evefarm.ui.ComboSizing;
import com.evefarm.ui.Icons;
import com.evefarm.ui.column.Isk;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FilterBarPanel extends JPanel {

    private static final String FILTERS = "Filters";

    private final String panelKey;
    private final List<String> columnNames;
    private final SavedFilterDao savedFilterDao;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<FilterRow> rows = new ArrayList<>();
    private final JPanel rowsPanel = new JPanel();
    private final JTextField searchField = new JTextField(18);
    private final JToggleButton filtersButton = new JToggleButton(FILTERS);
    private final JLabel countLabel = new JLabel(" ");
    private Runnable onFilterChanged = () -> {
    };

    public FilterBarPanel(String panelKey, List<String> columnNames, SavedFilterDao savedFilterDao) {
        super(new BorderLayout());
        this.panelKey = panelKey;
        this.columnNames = columnNames;
        this.savedFilterDao = savedFilterDao;

        ComboSizing.input(searchField);
        searchField.setToolTipText("Shows only the rows that contain this text in any column");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                fireChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                fireChanged();
            }
        });
        filtersButton.setIcon(Icons.FILTER);
        filtersButton.setToolTipText("Filters by column, such as Amount greater than 1,000,000");
        filtersButton.addActionListener(e -> showRows(filtersButton.isSelected()));
        JButton clearButton = new JButton("Clear", Icons.CLEAR);
        clearButton.setToolTipText("Clears the search and the filters");
        clearButton.addActionListener(e -> clearAll());
        JButton saveButton = new JButton("Save...", Icons.SAVE);
        saveButton.addActionListener(e -> saveCurrentFilter());
        JButton loadButton = new JButton("Load...", Icons.LOAD);
        loadButton.addActionListener(e -> loadSavedFilter());

        for (AbstractButton button : List.of(filtersButton, clearButton, saveButton, loadButton)) {
            button.putClientProperty("JButton.buttonType", "toolBarButton");
            button.setFocusable(false);
        }
        countLabel.putClientProperty("FlatLaf.styleClass", "small");
        countLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
        JLabel searchLabel = new JLabel("Search:", Icons.SEARCH, SwingConstants.LEADING);
        searchLabel.setLabelFor(searchField);
        searchLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        toolbar.add(searchLabel);
        toolbar.add(searchField);
        toolbar.add(filtersButton);
        toolbar.add(clearButton);
        toolbar.add(saveButton);
        toolbar.add(loadButton);
        toolbar.add(countLabel);

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        rowsPanel.setVisible(false);
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(toolbar, BorderLayout.NORTH);
        add(rowsPanel, BorderLayout.CENTER);

        addRow(new FilterCondition());
    }

    private void showRows(boolean show) {
        filtersButton.setSelected(show);
        rowsPanel.setVisible(show);
        revalidate();
        repaint();
    }

    public void setOnFilterChanged(Runnable onFilterChanged) {
        this.onFilterChanged = onFilterChanged;
    }

    public void setRowCounts(int shown, int total) {
        countLabel.setText("Showing " + shown + " of " + total);
    }

    private void addRow(FilterCondition initial) {
        FilterRow[] self = new FilterRow[1];
        FilterRow row = new FilterRow(columnNames, this::fireChanged, () -> {
            addRow(new FilterCondition());
            fireChanged();
        }, () -> removeRow(self[0]));
        self[0] = row;
        row.applyCondition(initial);
        rows.add(row);
        rowsPanel.add(row);
        renumberRows();
        revalidate();
        repaint();
    }

    private void removeRow(FilterRow row) {
        if (rows.size() <= 1) {
            return;
        }
        rows.remove(row);
        rowsPanel.remove(row);
        renumberRows();
        revalidate();
        repaint();
        fireChanged();
    }

    private void renumberRows() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setFirstRow(i == 0);
            rows.get(i).setRemoveEnabled(rows.size() > 1);
        }
    }

    private void clearAll() {
        searchField.setText("");
        rows.clear();
        rowsPanel.removeAll();
        addRow(new FilterCondition());
        fireChanged();
    }

    private void fireChanged() {
        int active = activeConditions().size();
        filtersButton.setText(active == 0 ? FILTERS : FILTERS + " (" + active + ")");
        onFilterChanged.run();
    }

    private List<FilterCondition> activeConditions() {
        List<FilterCondition> active = new ArrayList<>();
        for (FilterRow row : rows) {
            FilterCondition condition = row.toCondition();
            if (condition.isEnabled() && condition.getValue() != null && !condition.getValue().isBlank()) {
                active.add(condition);
            }
        }
        return active;
    }

    static FilterCondition search(String text) {
        FilterCondition condition = new FilterCondition();
        condition.setColumn(FilterCondition.ALL_COLUMNS);
        condition.setOperator(FilterCondition.Operator.CONTAINS);
        condition.setValue(text.trim());
        return condition;
    }

    public RowFilter<Object, Object> buildRowFilter() {
        List<FilterCondition> active = activeConditions();
        FilterCondition search = searchField.getText().isBlank() ? null : search(searchField.getText());
        if (active.isEmpty() && search == null) {
            return null;
        }
        return new RowFilter<>() {
            @Override
            public boolean include(Entry<?, ?> entry) {
                if (search != null && !matches(entry, search, columnNames)) {
                    return false;
                }
                boolean result = true;
                for (int i = 0; i < active.size(); i++) {
                    FilterCondition condition = active.get(i);
                    boolean matches = matches(entry, condition, columnNames);
                    if (i == 0) {
                        result = matches;
                    } else if (condition.getLogicOp() == FilterCondition.LogicOp.OR) {
                        result = result || matches;
                    } else {
                        result = result && matches;
                    }
                }
                return result;
            }
        };
    }

    static boolean matches(RowFilter.Entry<?, ?> entry, FilterCondition condition, List<String> columnNames) {
        if (FilterCondition.ALL_COLUMNS.equals(condition.getColumn())) {
            boolean negated = condition.getOperator() == FilterCondition.Operator.NOT_EQUALS;
            for (int col = 0; col < entry.getValueCount(); col++) {
                if (matchesCell(entry.getValue(col), condition) != negated) {
                    return !negated;
                }
            }
            return negated;
        }
        int col = columnNames.indexOf(condition.getColumn());
        if (col < 0) {
            return true;
        }
        return matchesCell(entry.getValue(col), condition);
    }

    static String cellText(Object cellValue) {
        return cellValue == null || cellValue instanceof Icon ? "" : cellValue.toString();
    }

    private static boolean matchesCell(Object cellValue, FilterCondition condition) {
        String text = cellText(cellValue);
        String needle = condition.getValue();
        return switch (condition.getOperator()) {
            case CONTAINS -> text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
            case NOT_EQUALS -> !(text.equalsIgnoreCase(needle) || compareNumeric(cellValue, needle) == 0);
            case STARTS_WITH -> text.toLowerCase(Locale.ROOT).startsWith(needle.toLowerCase(Locale.ROOT));
            case ENDS_WITH -> text.toLowerCase(Locale.ROOT).endsWith(needle.toLowerCase(Locale.ROOT));
            case EQUALS -> text.equalsIgnoreCase(needle) || compareNumeric(cellValue, needle) == 0;
            case GREATER_THAN -> compareNumeric(cellValue, needle) > 0;
            case LESS_THAN -> compareNumeric(cellValue, needle) < 0;
        };
    }

    private static int compareNumeric(Object cellValue, String needle) {
        Double cellNumber = toNumber(cellValue);
        Double needleNumber = toNumber(needle);
        if (cellNumber != null && needleNumber != null) {
            return Double.compare(cellNumber, needleNumber);
        }
        return cellText(cellValue).compareToIgnoreCase(needle);
    }

    private static Double toNumber(Object value) {
        if (value instanceof Isk isk && isk.isEmpty()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = cellText(value).replaceAll("[^0-9.\\-]", "");
        if (text.isBlank() || "-".equals(text) || ".".equals(text)) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveCurrentFilter() {
        String name = JOptionPane.showInputDialog(this, "Filter name:", "Save Filter",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            List<FilterCondition> conditions = new ArrayList<>();
            if (!searchField.getText().isBlank()) {
                conditions.add(search(searchField.getText()));
            }
            rows.stream().map(FilterRow::toCondition).forEach(conditions::add);
            String json = objectMapper.writeValueAsString(conditions);
            savedFilterDao.save(panelKey, name.trim(), json);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Failed to save filter: " + e.getMessage(),
                    "Save Filter", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadSavedFilter() {
        List<String> names = savedFilterDao.listNames(panelKey);
        if (names.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No saved filters yet.", "Load Filter",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<String> combo = new JComboBox<>(names.toArray(new String[0]));
        ComboSizing.options(combo);
        int result = JOptionPane.showConfirmDialog(this, combo, "Load Filter",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }
        String selected = (String) combo.getSelectedItem();
        savedFilterDao.find(panelKey, selected).ifPresent(json -> {
            try {
                FilterCondition[] conditions = objectMapper.readValue(json, FilterCondition[].class);
                searchField.setText("");
                rows.clear();
                rowsPanel.removeAll();
                showRows(true);
                if (conditions.length == 0) {
                    addRow(new FilterCondition());
                } else {
                    for (FilterCondition condition : conditions) {
                        addRow(condition);
                    }
                }
                fireChanged();
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Failed to load filter: " + e.getMessage(),
                        "Load Filter", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
