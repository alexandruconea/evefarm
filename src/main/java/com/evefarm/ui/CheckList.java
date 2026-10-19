package com.evefarm.ui;

import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JList;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

public final class CheckList<T> {

    private final DefaultListModel<T> listModel = new DefaultListModel<>();
    private final JList<T> list = new JList<>(listModel);
    private final Set<String> checked = new LinkedHashSet<>();
    private final Function<T, String> key;

    public CheckList(List<T> items, Collection<String> checkedKeys, Function<T, String> key,
                     Function<T, String> label, Function<T, String> toolTip) {
        this.key = key;
        items.forEach(listModel::addElement);
        checked.addAll(checkedKeys);
        list.setCellRenderer((jList, item, index, isSelected, cellHasFocus) -> {
            JCheckBox box = new JCheckBox(label.apply(item), checked.contains(key.apply(item)));
            box.setToolTipText(toolTip.apply(item));
            box.setBackground(isSelected ? jList.getSelectionBackground() : jList.getBackground());
            box.setForeground(isSelected ? jList.getSelectionForeground() : jList.getForeground());
            return box;
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index < 0) {
                    return;
                }
                String clicked = key.apply(listModel.get(index));
                if (!checked.remove(clicked)) {
                    checked.add(clicked);
                }
                list.repaint();
            }
        });
    }

    public JList<T> list() {
        return list;
    }

    public void move(int delta) {
        int index = list.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= listModel.size()) {
            return;
        }
        T item = listModel.remove(index);
        listModel.add(target, item);
        list.setSelectedIndex(target);
    }

    public void checkAll(boolean check) {
        checked.clear();
        if (check) {
            items().forEach(item -> checked.add(key.apply(item)));
        }
        list.repaint();
    }

    public List<T> items() {
        List<T> items = new ArrayList<>(listModel.size());
        for (int i = 0; i < listModel.size(); i++) {
            items.add(listModel.get(i));
        }
        return items;
    }

    public boolean isChecked(T item) {
        return checked.contains(key.apply(item));
    }
}
