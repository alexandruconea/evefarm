package com.evefarm.ui;

import com.evefarm.auth.TokenCipher;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.PriceMode;
import com.evefarm.service.PriceService;
import com.evefarm.util.AppPaths;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.net.URI;

public final class SettingsDialog extends JDialog {

    private static final int FIELD_WIDTH = 320;
    private static final int FIELD_HEIGHT = 26;
    private static final int BACKUP_NOTE_WIDTH = 520;

    private final SettingsDao settingsDao;
    private final JComboBox<String> themeCombo = new JComboBox<>(new String[]{
            "flatlaf-light", "flatlaf-dark", "system"});
    private final JComboBox<String> priceProviderCombo = new JComboBox<>(new String[]{
            "CCP (ESI, global average)", "Fuzzwork (Jita 4-4 sell price)", "Janice (Jita 4-4, requires API key)"});
    private final JPasswordField janiceApiKeyField = new JPasswordField();
    private final JButton janiceGetApiKeyButton = new JButton("How do I get one?");
    private final JComboBox<PriceMode> defaultPriceCombo = new JComboBox<>(PriceMode.values());
    private final JSpinner snapshotIntervalSpinner = new JSpinner(new SpinnerNumberModel(60, 5, 1440, 5));
    private final JTextField gameLogDirectoryField = new JTextField();
    private final JButton gameLogDirectoryBrowseButton = new JButton("Browse...");
    private final JTextField backupCopyDirectoryField = new JTextField();
    private final JButton backupCopyDirectoryBrowseButton = new JButton("Browse...");
    private final JCheckBox updateCheckBox = new JCheckBox("Check for new versions automatically");

    public SettingsDialog(Frame owner, SettingsDao settingsDao) {
        super(owner, "Settings", true);
        this.settingsDao = settingsDao;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        loadCurrentValues();
        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel buildForm() {
        fixWidth(themeCombo, FIELD_WIDTH);
        fixWidth(priceProviderCombo, FIELD_WIDTH);
        fixWidth(defaultPriceCombo, FIELD_WIDTH);
        fixWidth(snapshotIntervalSpinner, FIELD_WIDTH);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;

        int row = 0;

        addRow(panel, c, row++, "Theme:", themeCombo);
        addRow(panel, c, row++, "Price provider:", priceProviderCombo);

        JPanel janiceKeyPanel = new JPanel(new BorderLayout(4, 0));
        janiceKeyPanel.setPreferredSize(new Dimension(FIELD_WIDTH, FIELD_HEIGHT));
        janiceKeyPanel.add(janiceApiKeyField, BorderLayout.CENTER);
        janiceKeyPanel.add(janiceGetApiKeyButton, BorderLayout.EAST);
        addRow(panel, c, row++, "Janice API key:", janiceKeyPanel);

        addRow(panel, c, row++, "Default price:", defaultPriceCombo);
        addRow(panel, c, row++, "Snapshot interval (minutes):", snapshotIntervalSpinner);

        JPanel gameLogPanel = new JPanel(new BorderLayout(4, 0));
        gameLogPanel.setPreferredSize(new Dimension(FIELD_WIDTH, FIELD_HEIGHT));
        gameLogPanel.add(gameLogDirectoryField, BorderLayout.CENTER);
        gameLogPanel.add(gameLogDirectoryBrowseButton, BorderLayout.EAST);
        addRow(panel, c, row++, "EVE Gamelogs folder (Kills tab):", gameLogPanel);

        JPanel backupCopyPanel = new JPanel(new BorderLayout(4, 0));
        backupCopyPanel.setPreferredSize(new Dimension(FIELD_WIDTH, FIELD_HEIGHT));
        backupCopyPanel.add(backupCopyDirectoryField, BorderLayout.CENTER);
        backupCopyPanel.add(backupCopyDirectoryBrowseButton, BorderLayout.EAST);
        addRow(panel, c, row++, "Extra backup copy folder:", backupCopyPanel);
        addRow(panel, c, row++, "Updates:", updateCheckBox);

        JTextArea backupNote = new JTextArea("Automatic backups run once a day to " + AppPaths.backupDir()
                + " (last 7 days + one per month for 12 months) - always, no setup needed. The extra folder "
                + "is optional: any folder on another drive, a USB stick or a OneDrive/Google Drive folder gets "
                + "a second copy. Restore any of them with Options > Restore Data.");
        backupNote.setEditable(false);
        backupNote.setFocusable(false);
        backupNote.setOpaque(false);
        backupNote.setLineWrap(true);
        backupNote.setWrapStyleWord(true);
        backupNote.setFont(new JLabel().getFont().deriveFont(new JLabel().getFont().getSize2D() - 1f));
        backupNote.setSize(new Dimension(BACKUP_NOTE_WIDTH, Short.MAX_VALUE));
        backupNote.setPreferredSize(new Dimension(BACKUP_NOTE_WIDTH, backupNote.getPreferredSize().height));
        c.gridx = 0;
        c.gridy = row++;
        c.gridwidth = 2;
        panel.add(backupNote, c);
        c.gridwidth = 1;

        priceProviderCombo.addActionListener(e -> updateJaniceFieldEnabled());
        janiceGetApiKeyButton.addActionListener(e -> showJaniceApiKeyHelp());
        gameLogDirectoryBrowseButton.addActionListener(e -> browseForGameLogDirectory());
        backupCopyDirectoryBrowseButton.addActionListener(e -> browseForBackupCopyDirectory());

        return panel;
    }

    private void fixWidth(JComponent component, int width) {
        Dimension size = new Dimension(width, FIELD_HEIGHT);
        component.setPreferredSize(size);
        component.setMinimumSize(size);
        component.setMaximumSize(size);
    }

    private void updateJaniceFieldEnabled() {
        boolean janiceSelected = priceProviderCombo.getSelectedIndex() == 2;
        janiceApiKeyField.setEnabled(janiceSelected);
        janiceGetApiKeyButton.setEnabled(janiceSelected);
    }

    private void browseForGameLogDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("EVE Gamelogs folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = gameLogDirectoryField.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            gameLogDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void browseForBackupCopyDirectory() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Extra backup copy folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        String current = backupCopyDirectoryField.getText().trim();
        if (!current.isEmpty()) {
            chooser.setCurrentDirectory(new File(current));
        }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            backupCopyDirectoryField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void showJaniceApiKeyHelp() {
        String discordUrl = "https://discord.gg/7McHR3r";
        int result = JOptionPane.showConfirmDialog(this,
                "Janice has no public/anonymous API - every account needs its own personal key.\n"
                        + "Request one by messaging the Janice team on their Discord: " + discordUrl + "\n\n"
                        + "Open that link now?",
                "Janice API Key", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (result == JOptionPane.YES_OPTION) {
            try {
                Desktop.getDesktop().browse(URI.create(discordUrl));
            } catch (Exception ignored) {
            }
        }
    }

    private void addRow(JPanel panel, GridBagConstraints c, int row, String label, Component field) {
        c.gridx = 0;
        c.gridy = row;
        panel.add(new JLabel(label), c);
        c.gridx = 1;
        panel.add(field, c);
    }

    private JPanel buildButtons() {
        JPanel panel = new JPanel();
        JButton save = new JButton("Save", Icons.SAVE);
        save.addActionListener(e -> save());
        JButton cancel = new JButton("Cancel", Icons.CANCEL);
        cancel.addActionListener(e -> dispose());
        panel.add(ButtonSizing.row(6, save, cancel));
        return panel;
    }

    private void loadCurrentValues() {
        themeCombo.setSelectedItem(settingsDao.getOrDefault(SettingsDao.LAF_THEME, "flatlaf-light"));
        String provider = settingsDao.getOrDefault(SettingsDao.PRICE_PROVIDER, PriceService.PROVIDER_CCP);
        priceProviderCombo.setSelectedIndex(providerIndex(provider));
        janiceApiKeyField.setText(TokenCipher.decrypt(settingsDao.getOrDefault(SettingsDao.JANICE_API_KEY, "")));
        updateJaniceFieldEnabled();
        String priceMode = settingsDao.getOrDefault(SettingsDao.DEFAULT_PRICE_MODE, PriceMode.SELL_AVG.name());
        try {
            defaultPriceCombo.setSelectedItem(PriceMode.valueOf(priceMode));
        } catch (IllegalArgumentException e) {
            defaultPriceCombo.setSelectedItem(PriceMode.SELL_AVG);
        }
        Integer snapshotInterval = parseIntSetting(SettingsDao.SNAPSHOT_INTERVAL_MINUTES);
        if (snapshotInterval != null) {
            snapshotIntervalSpinner.setValue(snapshotInterval);
        }
        gameLogDirectoryField.setText(settingsDao.getOrDefault(
                SettingsDao.GAMELOG_DIRECTORY, AppPaths.defaultGameLogDirectory().toString()));
        backupCopyDirectoryField.setText(settingsDao.getOrDefault(SettingsDao.BACKUP_COPY_DIRECTORY, ""));
        updateCheckBox.setSelected(!"false".equals(settingsDao.getOrDefault(SettingsDao.UPDATE_AUTO_CHECK, "true")));
    }

    private Integer parseIntSetting(String key) {
        try {
            return settingsDao.get(key).map(Integer::parseInt).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int providerIndex(String provider) {
        if (PriceService.PROVIDER_FUZZWORK.equals(provider)) {
            return 1;
        }
        if (PriceService.PROVIDER_JANICE.equals(provider)) {
            return 2;
        }
        return 0;
    }

    private void save() {
        settingsDao.set(SettingsDao.LAF_THEME, (String) themeCombo.getSelectedItem());
        String provider = switch (priceProviderCombo.getSelectedIndex()) {
            case 1 -> PriceService.PROVIDER_FUZZWORK;
            case 2 -> PriceService.PROVIDER_JANICE;
            default -> PriceService.PROVIDER_CCP;
        };
        settingsDao.set(SettingsDao.PRICE_PROVIDER, provider);
        String janiceKey = new String(janiceApiKeyField.getPassword()).trim();
        settingsDao.set(SettingsDao.JANICE_API_KEY, janiceKey.isEmpty() ? "" : TokenCipher.encrypt(janiceKey));
        PriceMode priceMode = (PriceMode) defaultPriceCombo.getSelectedItem();
        settingsDao.set(SettingsDao.DEFAULT_PRICE_MODE, priceMode.name());
        settingsDao.set(SettingsDao.SNAPSHOT_INTERVAL_MINUTES, String.valueOf(snapshotIntervalSpinner.getValue()));
        settingsDao.set(SettingsDao.GAMELOG_DIRECTORY, gameLogDirectoryField.getText().trim());
        settingsDao.set(SettingsDao.BACKUP_COPY_DIRECTORY, backupCopyDirectoryField.getText().trim());
        settingsDao.set(SettingsDao.UPDATE_AUTO_CHECK, String.valueOf(updateCheckBox.isSelected()));

        JOptionPane.showMessageDialog(this,
                "Saved. Restart EVE Farm for theme changes to take effect. For price provider or "
                        + "default price changes, use Update -> Market Prices -> Force to refresh right away.\n\n"
                        + "Note: \"Default price\" only affects Fuzzwork/Janice - CCP has no buy/sell "
                        + "breakdown to choose from.",
                "Settings saved", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
