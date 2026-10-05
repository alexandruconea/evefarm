package com.evefarm.ui;

import com.evefarm.model.BlueprintChoice;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

final class BlueprintChooserDialog extends JDialog {

    private static final int MAX_SHOWN = 500;

    private final List<BlueprintChoice> choices;
    private final JTextField searchField = new JTextField();
    private final DefaultListModel<BlueprintChoice> listModel = new DefaultListModel<>();
    private final JList<BlueprintChoice> list = new JList<>(listModel);
    private final JLabel countLabel = new JLabel(" ");
    private final JButton chooseButton = new JButton("Choose", Icons.ADD);
    private BlueprintChoice chosen;

    BlueprintChooserDialog(Window owner, List<BlueprintChoice> choices) {
        super(owner, "Choose a Blueprint", ModalityType.APPLICATION_MODAL);
        this.choices = choices;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUi();
        filter();
        setSize(new Dimension(560, 600));
        setLocationRelativeTo(owner);
    }

    BlueprintChoice choose() {
        setVisible(true);
        return chosen;
    }

    private void buildUi() {
        searchField.putClientProperty("JTextField.placeholderText", "Search by what it makes, for example Jaguar");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
        searchField.addActionListener(e -> {
            if (list.getSelectedValue() == null && !listModel.isEmpty()) {
                list.setSelectedIndex(0);
            }
            accept();
        });
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> source, Object value, int index,
                                                          boolean isSelected, boolean hasFocus) {
                super.getListCellRendererComponent(source, value, index, isSelected, hasFocus);
                if (value instanceof BlueprintChoice choice) {
                    setText(choice.productName() + "   ·   " + (choice.groupName() == null ? "" : choice.groupName()));
                }
                return this;
            }
        });
        list.putClientProperty("html.disable", Boolean.TRUE);
        list.addListSelectionListener(e -> chooseButton.setEnabled(list.getSelectedValue() != null));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                    accept();
                }
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        top.add(searchField, BorderLayout.NORTH);
        top.add(countLabel, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8),
                scroll.getBorder()));

        chooseButton.setEnabled(false);
        chooseButton.addActionListener(e -> accept());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(chooseButton);
        buttons.add(cancel);

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        getRootPane().setDefaultButton(chooseButton);
    }

    private void filter() {
        String text = searchField.getText().strip().toLowerCase(Locale.ROOT);
        listModel.clear();
        int matches = 0;
        for (BlueprintChoice choice : choices) {
            if (text.isEmpty() || choice.productName().toLowerCase(Locale.ROOT).contains(text)) {
                matches++;
                if (listModel.size() < MAX_SHOWN) {
                    listModel.addElement(choice);
                }
            }
        }
        countLabel.setText(matches > MAX_SHOWN ? matches + " blueprints - type to narrow the list"
                : matches + " blueprint" + (matches == 1 ? "" : "s"));
    }

    private void accept() {
        chosen = list.getSelectedValue();
        if (chosen != null) {
            dispose();
        }
    }
}
