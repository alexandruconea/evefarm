package com.evefarm.ui;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.MutableComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.IntStream;

final class FilteredComboModel<E> extends DefaultComboBoxModel<E> implements ListDataListener {

    private final ComboBoxModel<E> source;
    private final transient Function<Object, String> text;
    private String filter = "";
    private int[] shown;

    FilteredComboModel(ComboBoxModel<E> source, Function<Object, String> text) {
        this.source = source;
        this.text = text;
        source.addListDataListener(this);
    }

    ComboBoxModel<E> source() {
        return source;
    }

    void detach() {
        source.removeListDataListener(this);
    }

    String filter() {
        return filter;
    }

    boolean filtering() {
        return shown != null;
    }

    void setFilter(String value) {
        filter = value;
        shown = value.isBlank() ? null : matches(value.strip().toLowerCase(Locale.ROOT));
        fireContentsChanged(this, -1, -1);
    }

    static boolean matches(String itemText, String needle) {
        String haystack = itemText.toLowerCase(Locale.ROOT);
        return Arrays.stream(needle.split("\\s+")).allMatch(haystack::contains);
    }

    private int[] matches(String needle) {
        return IntStream.range(0, source.getSize())
                .filter(i -> matches(Objects.requireNonNullElse(text.apply(source.getElementAt(i)), ""), needle))
                .toArray();
    }

    private void clear() {
        if (shown != null || !filter.isEmpty()) {
            setFilter("");
        }
    }

    private MutableComboBoxModel<E> mutable() {
        clear();
        if (source instanceof MutableComboBoxModel<E> model) {
            return model;
        }
        throw new UnsupportedOperationException("This list can't be changed");
    }

    @Override
    public int getSize() {
        return shown == null ? source.getSize() : shown.length;
    }

    @Override
    public E getElementAt(int index) {
        if (shown == null) {
            return source.getElementAt(index);
        }
        return index >= 0 && index < shown.length ? source.getElementAt(shown[index]) : null;
    }

    @Override
    public Object getSelectedItem() {
        return source.getSelectedItem();
    }

    @Override
    public void setSelectedItem(Object item) {
        clear();
        source.setSelectedItem(item);
    }

    @Override
    public int getIndexOf(Object item) {
        for (int i = 0; i < getSize(); i++) {
            if (Objects.equals(getElementAt(i), item)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addElement(E item) {
        mutable().addElement(item);
    }

    @Override
    public void insertElementAt(E item, int index) {
        mutable().insertElementAt(item, index);
    }

    @Override
    public void removeElementAt(int index) {
        mutable().removeElementAt(index);
    }

    @Override
    public void removeElement(Object item) {
        mutable().removeElement(item);
    }

    @Override
    public void removeAllElements() {
        MutableComboBoxModel<E> model = mutable();
        if (model instanceof DefaultComboBoxModel<E> plain) {
            plain.removeAllElements();
        } else {
            while (model.getSize() > 0) {
                model.removeElementAt(model.getSize() - 1);
            }
        }
    }

    @Override
    public void addAll(Collection<? extends E> items) {
        MutableComboBoxModel<E> model = mutable();
        if (model instanceof DefaultComboBoxModel<E> plain) {
            plain.addAll(items);
        } else {
            items.forEach(model::addElement);
        }
    }

    @Override
    public void addAll(int index, Collection<? extends E> items) {
        MutableComboBoxModel<E> model = mutable();
        if (model instanceof DefaultComboBoxModel<E> plain) {
            plain.addAll(index, items);
        } else {
            int at = index;
            for (E item : items) {
                model.insertElementAt(item, at++);
            }
        }
    }

    @Override
    public void intervalAdded(ListDataEvent e) {
        if (shown == null) {
            fireIntervalAdded(this, e.getIndex0(), e.getIndex1());
        } else {
            setFilter(filter);
        }
    }

    @Override
    public void intervalRemoved(ListDataEvent e) {
        if (shown == null) {
            fireIntervalRemoved(this, e.getIndex0(), e.getIndex1());
        } else {
            setFilter(filter);
        }
    }

    @Override
    public void contentsChanged(ListDataEvent e) {
        if (shown == null) {
            fireContentsChanged(this, e.getIndex0(), e.getIndex1());
        } else {
            setFilter(filter);
        }
    }
}
