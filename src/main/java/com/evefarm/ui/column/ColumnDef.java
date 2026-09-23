package com.evefarm.ui.column;

import java.util.function.Function;

public record ColumnDef<T>(String key, String label, Class<?> type, boolean visibleByDefault, Function<T, Object> getter) {

    public ColumnDef(String key, String label, Class<?> type, Function<T, Object> getter) {
        this(key, label, type, true, getter);
    }
}
