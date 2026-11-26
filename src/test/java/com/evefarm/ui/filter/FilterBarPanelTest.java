package com.evefarm.ui.filter;

import com.evefarm.ui.column.Isk;
import org.junit.jupiter.api.Test;

import javax.swing.ImageIcon;
import javax.swing.RowFilter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void theSearchLooksInEveryColumnWithoutCaringForCase() {
        RowFilter.Entry<Object, Integer> row = row("Malpais Legate", Isk.of(26_378_176.31));

        assertTrue(FilterBarPanel.matches(row, FilterBarPanel.search("  LEGATE "), COLUMNS));
        assertTrue(FilterBarPanel.matches(row, FilterBarPanel.search("26,378,176"), COLUMNS));
        assertFalse(FilterBarPanel.matches(row, FilterBarPanel.search("Barset"), COLUMNS));
    }

    @Test
    void iconsAndEmptyCellsAreNotFoundAsText() {
        RowFilter.Entry<Object, Integer> row = row(new ImageIcon(new byte[0]), null);

        for (String text : List.of("con", "ima", "javax", "null", "@")) {
            assertFalse(FilterBarPanel.matches(row, FilterBarPanel.search(text), COLUMNS), text);
        }
        assertFalse(FilterBarPanel.matches(row, condition(FilterCondition.ALL_COLUMNS,
                FilterCondition.Operator.GREATER_THAN, "0"), COLUMNS), "an icon has no number");
    }

    @Test
    void theSearchAndTheFiltersStartEmpty() {
        assertNull(new FilterBarPanel("journal", COLUMNS, null).buildRowFilter());
    }
}
