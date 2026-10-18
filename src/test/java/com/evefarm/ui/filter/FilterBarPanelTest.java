package com.evefarm.ui.filter;

import org.junit.jupiter.api.Test;

import javax.swing.RowFilter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterBarPanelTest {

    private static final List<String> COLUMNS = List.of("Character", "Amount");

    private static RowFilter.Entry<Object, Integer> row(Object... values) {
        return new RowFilter.Entry<>() {
            @Override
            public Object getModel() {
                return null;
            }

            @Override
            public int getValueCount() {
                return values.length;
            }

            @Override
            public Object getValue(int index) {
                return values[index];
            }

            @Override
            public Integer getIdentifier() {
                return 0;
            }
        };
    }

    private static FilterCondition condition(String column, FilterCondition.Operator operator, String value) {
        FilterCondition condition = new FilterCondition();
        condition.setColumn(column);
        condition.setOperator(operator);
        condition.setValue(value);
        return condition;
    }

    @Test
    void notEqualsIsTheOppositeOfEqualsAlsoForNumbers() {
        RowFilter.Entry<Object, Integer> row = row("Malpais Legate", 100.0);

        assertTrue(FilterBarPanel.matches(row, condition("Amount", FilterCondition.Operator.EQUALS, "100"), COLUMNS));
        assertFalse(FilterBarPanel.matches(row,
                condition("Amount", FilterCondition.Operator.NOT_EQUALS, "100"), COLUMNS));
        assertTrue(FilterBarPanel.matches(row,
                condition("Amount", FilterCondition.Operator.NOT_EQUALS, "250"), COLUMNS));
    }

    @Test
    void notEqualsOnAllColumnsLeavesOutRowsWhereAnyColumnEqualsTheValue() {
        RowFilter.Entry<Object, Integer> row = row("Malpais Legate", 100.0);

        assertFalse(FilterBarPanel.matches(row, condition(FilterCondition.ALL_COLUMNS,
                FilterCondition.Operator.NOT_EQUALS, "malpais legate"), COLUMNS));
        assertTrue(FilterBarPanel.matches(row, condition(FilterCondition.ALL_COLUMNS,
                FilterCondition.Operator.NOT_EQUALS, "Sabina Preda"), COLUMNS));
    }

    @Test
    void otherConditionsOnAllColumnsMatchWhenAnyColumnMatches() {
        RowFilter.Entry<Object, Integer> row = row("Malpais Legate", 100.0);

        assertTrue(FilterBarPanel.matches(row, condition(FilterCondition.ALL_COLUMNS,
                FilterCondition.Operator.CONTAINS, "legate"), COLUMNS));
        assertFalse(FilterBarPanel.matches(row, condition(FilterCondition.ALL_COLUMNS,
                FilterCondition.Operator.CONTAINS, "preda"), COLUMNS));
    }
}
