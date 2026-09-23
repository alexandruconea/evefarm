package com.evefarm.ui.filter;

public final class FilterCondition {

    public enum LogicOp {
        AND("And"), OR("Or");

        private final String label;

        LogicOp(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Operator {
        CONTAINS("Contains"),
        EQUALS("Equals"),
        NOT_EQUALS("Not Equals"),
        STARTS_WITH("Starts With"),
        ENDS_WITH("Ends With"),
        GREATER_THAN("Greater Than"),
        LESS_THAN("Less Than");

        private final String label;

        Operator(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static final String ALL_COLUMNS = "All";

    private boolean enabled = true;
    private LogicOp logicOp = LogicOp.AND;
    private String column = ALL_COLUMNS;
    private Operator operator = Operator.CONTAINS;
    private String value = "";

    public FilterCondition() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LogicOp getLogicOp() {
        return logicOp;
    }

    public void setLogicOp(LogicOp logicOp) {
        this.logicOp = logicOp;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
