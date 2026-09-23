package com.evefarm.ui;

import com.evefarm.db.dao.SettingsDao;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;

public final class LpTargetDialog extends JDialog {

    private final SettingsDao settingsDao;
    private final JSpinner targetSpinner = new JSpinner(new SpinnerNumberModel(500.0, 0.0, 1_000_000_000.0, 50.0));
    private final Runnable onSaved;

    public LpTargetDialog(Window owner, SettingsDao settingsDao, Runnable onSaved) {
        super(owner, "Set ISK/LP Target", ModalityType.APPLICATION_MODAL);
        this.settingsDao = settingsDao;
        this.onSaved = onSaved;

        double current = Double.parseDouble(settingsDao.getOrDefault(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, "500"));
        targetSpinner.setValue(current);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        form.add(new JLabel("Target ISK/LP:"));
        form.add(targetSpinner);

        JLabel help = new JLabel("<html><body style='width: 260px'>Offers at or above this are "
                + "shown in green in the ISK/LP columns; offers below it are shown in red. Set "
                + "it to 0 to turn the coloring off.</body></html>");
        help.setBorder(BorderFactory.createEmptyBorder(0, 12, 8, 12));

        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(ButtonSizing.row(6, save, cancel));

        setLayout(new BorderLayout());
        add(help, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private void save() {
        double value = ((Number) targetSpinner.getValue()).doubleValue();
        settingsDao.set(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, String.valueOf(value));
        dispose();
        onSaved.run();
    }
}
