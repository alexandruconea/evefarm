package com.evefarm.ui;

import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.ContainerEvent;

public final class ComboBoxFocus {

    public static void hideDashedOutline() {
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event.getID() == ContainerEvent.COMPONENT_ADDED
                    && ((ContainerEvent) event).getChild() instanceof JComboBox<?> combo) {
                plainCurrentValue(combo);
            }
        }, AWTEvent.CONTAINER_EVENT_MASK);
    }

    @SuppressWarnings("unchecked")
    static void plainCurrentValue(JComboBox<?> combo) {
        ListCellRenderer<?> renderer = combo.getRenderer();
        if (renderer == null || renderer instanceof PlainCurrentValue) {
            return;
        }
        ((JComboBox<Object>) combo).setRenderer(new PlainCurrentValue((ListCellRenderer<Object>) renderer));
    }

    private record PlainCurrentValue(ListCellRenderer<Object> delegate) implements ListCellRenderer<Object> {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected,
                                                      boolean cellHasFocus) {
            boolean inList = index >= 0;
            return delegate.getListCellRendererComponent(list, value, index, isSelected && inList,
                    cellHasFocus && inList);
        }
    }

    private ComboBoxFocus() {
    }
}
