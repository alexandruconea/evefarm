package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.EveSettingsService.CopyRequest;
import com.evefarm.service.EveSettingsService.CopyResult;
import com.evefarm.service.EveSettingsService.Profile;
import com.evefarm.service.EveSettingsService.ScanResult;
import com.evefarm.service.EveSettingsService.SettingsFile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EveSettingsCopyDialog extends JDialog {

    private static final Logger LOG = Logger.getLogger(EveSettingsCopyDialog.class.getName());
    private static final DateTimeFormatter MODIFIED_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final AppContext appContext;
    private final Long preferredSourceId;
    private final Map<Long, String> characterNames = new HashMap<>();

    private final JTextField directoryField = new JTextField();
    private final JComboBox<Profile> sourceProfile = new JComboBox<>();
    private final JComboBox<CharacterChoice> sourceCharacter = new JComboBox<>();
    private final JComboBox<Profile> targetProfile = new JComboBox<>();
    private final JComboBox<CharacterChoice> targetCharacter = new JComboBox<>();
    private final JCheckBox includeAccount = new JCheckBox("Include account-wide settings (core_user)");
    private final JComboBox<AccountChoice> sourceAccount = new JComboBox<>();
    private final JComboBox<AccountChoice> targetAccount = new JComboBox<>();
    private final JTextArea status = new JTextArea();
    private final JButton copyButton = new JButton("Copy Settings", Icons.SWAP);
    private final JButton browseButton = new JButton("Browse...");
    private final JButton rescanButton = new JButton("Rescan", Icons.REFRESH);
    private final JButton closeButton = new JButton("Close", Icons.CANCEL);

    private ScanResult scan;
    private SwingWorker<CopyResult, Void> copyWorker;
    private SwingWorker<Map<Long, String>, Void> nameWorker;
    private boolean updating;

    public EveSettingsCopyDialog(Window owner, AppContext appContext, Long preferredSourceId) {
        super(owner, "Copy EVE Settings", ModalityType.APPLICATION_MODAL);
        this.appContext = appContext;
        this.preferredSourceId = preferredSourceId;
        for (EveCharacter character : appContext.characterService.listCharacters()) {
            characterNames.put(character.characterId(), character.characterName());
        }
        buildUi();
        loadProfiles();
        pack();
        setMinimumSize(new Dimension(690, getHeight()));
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        directoryField.setEditable(false);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createEmptyBorder(10, 10, 6, 10));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(4, 4, 4, 4);
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;

        addRow(form, constraints, 0, "EVE settings folder:", directoryField,
                ButtonSizing.row(4, browseButton, rescanButton));
        addRow(form, constraints, 1, "Source launcher profile:", sourceProfile, null);
        addRow(form, constraints, 2, "Source character:", sourceCharacter, null);
        addRow(form, constraints, 3, "Target launcher profile:", targetProfile, null);
        addRow(form, constraints, 4, "Target character:", targetCharacter, null);

        constraints.gridx = 1;
        constraints.gridy = 5;
        constraints.gridwidth = 2;
        form.add(includeAccount, constraints);
        constraints.gridwidth = 1;

        addRow(form, constraints, 6, "Source account file:", sourceAccount, null);
        addRow(form, constraints, 7, "Target account file:", targetAccount, null);

        TextAreaStyler.informational(status);
        status.setRows(5);
        status.setBorder(BorderFactory.createEmptyBorder(4, 14, 8, 14));
        JScrollPane statusScroll = new JScrollPane(status);
        statusScroll.setBorder(null);
        statusScroll.setPreferredSize(new Dimension(650, 100));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(ButtonSizing.row(4, copyButton, closeButton));

        setLayout(new BorderLayout());
        add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(statusScroll, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.SOUTH);
        add(footer, BorderLayout.SOUTH);

        browseButton.addActionListener(e -> browse());
        rescanButton.addActionListener(e -> loadProfiles());
        sourceProfile.addActionListener(e -> profilesChanged());
        targetProfile.addActionListener(e -> profilesChanged());
        sourceCharacter.addActionListener(e -> sourceCharacterChanged());
        targetCharacter.addActionListener(e -> updateControls());
        includeAccount.addActionListener(e -> updateControls());
        sourceAccount.addActionListener(e -> updateControls());
        targetAccount.addActionListener(e -> updateControls());
        copyButton.addActionListener(e -> copySettings());
        closeButton.addActionListener(e -> {
            if (copyWorker == null || copyWorker.isDone()) {
                dispose();
            }
        });
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, int row, String label,
                               java.awt.Component field, java.awt.Component trailing) {
        constraints.gridy = row;
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(new JLabel(label), constraints);

        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);

        if (trailing != null) {
            constraints.gridx = 2;
            constraints.weightx = 0;
            panel.add(trailing, constraints);
        }
    }

    private void browse() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select the EVE folder ending in _tranquility");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (scan != null) {
            chooser.setCurrentDirectory(scan.settingsRoot().toFile());
        }
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            appContext.eveSettingsService.setSettingsDirectory(chooser.getSelectedFile().toPath());
            loadProfiles();
        } catch (Exception e) {
            showError("Invalid EVE settings folder", e);
        }
    }

    private void loadProfiles() {
        try {
            scan = appContext.eveSettingsService.scan();
            directoryField.setText(scan.settingsRoot().toString());
            updating = true;
            sourceProfile.removeAllItems();
            targetProfile.removeAllItems();
            for (Profile profile : scan.profiles()) {
                sourceProfile.addItem(profile);
                targetProfile.addItem(profile);
            }
            int preferredProfile = profileContaining(preferredSourceId);
            if (preferredProfile >= 0) {
                sourceProfile.setSelectedIndex(preferredProfile);
                targetProfile.setSelectedIndex(preferredProfile);
            }
            updating = false;
            profilesChanged();
            status.setText("Select a source and a target. EVE Farm creates a verified backup before replacing "
                    + "an existing file. Keep EVE Online closed during the operation.");
            resolveMissingCharacterNames();
        } catch (Exception e) {
            scan = null;
            directoryField.setText("");
            clearChoices();
            status.setText(e.getMessage() + "\nUse Browse to select the folder whose name ends in _tranquility.");
        }
        updateControls();
    }

    private void resolveMissingCharacterNames() {
        Set<Long> missingIds = scan.profiles().stream()
                .flatMap(profile -> profile.characterFiles().stream())
                .map(SettingsFile::id)
                .filter(id -> !characterNames.containsKey(id))
                .collect(Collectors.toSet());
        if (missingIds.isEmpty()) {
            return;
        }
        if (nameWorker != null && !nameWorker.isDone()) {
            nameWorker.cancel(true);
        }
        nameWorker = new SwingWorker<>() {
            @Override
            protected Map<Long, String> doInBackground() {
                return appContext.entityNameCacheService.resolveEntityNames(missingIds);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    get().forEach((id, name) -> {
                        if (name != null && !name.isBlank() && !name.equals("#" + id)) {
                            characterNames.put(id, name);
                        }
                    });
                    profilesChanged();
                } catch (Exception e) {
                    LOG.log(Level.FINE, "Could not resolve EVE character names", e);
                } finally {
                    nameWorker = null;
                }
            }
        };
        nameWorker.execute();
    }

    private int profileContaining(Long characterId) {
        if (characterId == null || scan == null) {
            return -1;
        }
        for (int index = 0; index < scan.profiles().size(); index++) {
            if (scan.profiles().get(index).characterFiles().stream().anyMatch(file -> file.id() == characterId)) {
                return index;
            }
        }
        return -1;
    }

    private void profilesChanged() {
        if (updating) {
            return;
        }
        updating = true;
        CharacterChoice oldSource = (CharacterChoice) sourceCharacter.getSelectedItem();
        CharacterChoice oldTarget = (CharacterChoice) targetCharacter.getSelectedItem();
        AccountChoice oldSourceAccount = (AccountChoice) sourceAccount.getSelectedItem();
        AccountChoice oldTargetAccount = (AccountChoice) targetAccount.getSelectedItem();

        sourceCharacter.removeAllItems();
        Profile source = (Profile) sourceProfile.getSelectedItem();
        if (source != null) {
            source.characterFiles().stream()
                    .map(this::characterChoice)
                    .sorted(Comparator.comparing(CharacterChoice::toString, String.CASE_INSENSITIVE_ORDER))
                    .forEach(sourceCharacter::addItem);
        }
        if (oldSource != null) {
            selectCharacter(sourceCharacter, oldSource.id());
        } else {
            selectCharacter(sourceCharacter, preferredSourceId);
        }

        refillAccounts(sourceAccount, source, oldSourceAccount);
        refillTargetCharacters(oldTarget);
        Profile target = (Profile) targetProfile.getSelectedItem();
        refillAccounts(targetAccount, target, oldTargetAccount);
        updating = false;
        updateControls();
    }

    private void sourceCharacterChanged() {
        if (updating) {
            return;
        }
        updating = true;
        CharacterChoice oldTarget = (CharacterChoice) targetCharacter.getSelectedItem();
        refillTargetCharacters(oldTarget);
        updating = false;
        updateControls();
    }

    private void refillTargetCharacters(CharacterChoice previous) {
        targetCharacter.removeAllItems();
        Profile profile = (Profile) targetProfile.getSelectedItem();
        CharacterChoice source = (CharacterChoice) sourceCharacter.getSelectedItem();
        if (profile == null) {
            return;
        }
        Map<Long, Path> paths = new LinkedHashMap<>();
        for (SettingsFile file : profile.characterFiles()) {
            paths.put(file.id(), file.path());
        }
        for (Long characterId : characterNames.keySet()) {
            paths.putIfAbsent(characterId, profile.directory().resolve("core_char_" + characterId + ".dat"));
        }
        paths.entrySet().stream()
                .filter(entry -> source == null || entry.getKey() != source.id())
                .map(entry -> new CharacterChoice(entry.getKey(), characterName(entry.getKey()), entry.getValue(),
                        java.nio.file.Files.exists(entry.getValue())))
                .sorted(Comparator.comparing(CharacterChoice::toString, String.CASE_INSENSITIVE_ORDER))
                .forEach(targetCharacter::addItem);
        if (previous != null) {
            selectCharacter(targetCharacter, previous.id());
        }
    }

    private void refillAccounts(JComboBox<AccountChoice> combo, Profile profile, AccountChoice previous) {
        combo.removeAllItems();
        if (profile == null) {
            return;
        }
        profile.accountFiles().stream()
                .map(AccountChoice::new)
                .sorted(Comparator.comparingLong(AccountChoice::id))
                .forEach(combo::addItem);
        if (previous != null) {
            for (int index = 0; index < combo.getItemCount(); index++) {
                if (combo.getItemAt(index).id() == previous.id()) {
                    combo.setSelectedIndex(index);
                    break;
                }
            }
        }
    }

    private CharacterChoice characterChoice(SettingsFile file) {
        return new CharacterChoice(file.id(), characterName(file.id()), file.path(), true);
    }

    private String characterName(long id) {
        return characterNames.getOrDefault(id, "Character #" + id);
    }

    private static void selectCharacter(JComboBox<CharacterChoice> combo, Long id) {
        if (id == null) {
            return;
        }
        for (int index = 0; index < combo.getItemCount(); index++) {
            if (combo.getItemAt(index).id() == id) {
                combo.setSelectedIndex(index);
                return;
            }
        }
    }

    private void updateControls() {
        boolean accountsAvailable = sourceAccount.getItemCount() > 0 && targetAccount.getItemCount() > 0;
        if (!accountsAvailable) {
            includeAccount.setSelected(false);
        }
        includeAccount.setEnabled(accountsAvailable && !isBusy());
        sourceAccount.setEnabled(includeAccount.isSelected() && !isBusy());
        targetAccount.setEnabled(includeAccount.isSelected() && !isBusy());
        boolean valid = scan != null && sourceCharacter.getSelectedItem() != null
                && targetCharacter.getSelectedItem() != null
                && (!includeAccount.isSelected()
                    || (sourceAccount.getSelectedItem() != null && targetAccount.getSelectedItem() != null));
        copyButton.setEnabled(valid && !isBusy());
        browseButton.setEnabled(!isBusy());
        rescanButton.setEnabled(!isBusy());
        closeButton.setEnabled(!isBusy());
        sourceProfile.setEnabled(!isBusy());
        targetProfile.setEnabled(!isBusy());
        sourceCharacter.setEnabled(!isBusy());
        targetCharacter.setEnabled(!isBusy());
    }

    private void copySettings() {
        Profile sourceProfileValue = (Profile) sourceProfile.getSelectedItem();
        CharacterChoice source = (CharacterChoice) sourceCharacter.getSelectedItem();
        CharacterChoice target = (CharacterChoice) targetCharacter.getSelectedItem();
        if (scan == null || sourceProfileValue == null || source == null || target == null) {
            return;
        }
        AccountChoice fromAccount = includeAccount.isSelected() ? (AccountChoice) sourceAccount.getSelectedItem() : null;
        AccountChoice toAccount = includeAccount.isSelected() ? (AccountChoice) targetAccount.getSelectedItem() : null;

        StringBuilder message = new StringBuilder("Copy all character settings from ")
                .append(source).append(" to ").append(target).append("?\n\n")
                .append("The target character file will be replaced.");
        if (!target.exists()) {
            message.append(" It will be created because it does not exist yet.");
        }
        if (fromAccount != null && toAccount != null) {
            message.append("\n\nAccount settings will also be copied from account ")
                    .append(fromAccount.id()).append(" to account ").append(toAccount.id())
                    .append(". This affects every character on the target account.");
        }
        message.append("\n\nEVE Online must remain closed. Existing targets are backed up automatically.");
        if (JOptionPane.showConfirmDialog(this, message.toString(), "Confirm settings copy",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }

        CopyRequest request = new CopyRequest(scan.settingsRoot(), source.path(), target.path(),
                fromAccount == null ? null : fromAccount.file().path(),
                toAccount == null ? null : toAccount.file().path());
        status.setText("Copying and verifying EVE settings...");
        copyWorker = new SwingWorker<>() {
            @Override
            protected CopyResult doInBackground() throws Exception {
                return appContext.eveSettingsService.copy(request);
            }

            @Override
            protected void done() {
                try {
                    CopyResult result = get();
                    String backup = result.backupDirectory()
                            .map(path -> "\nBackup: " + path)
                            .orElse("\nNo previous target files needed a backup.");
                    loadProfiles();
                    status.setText("Settings copied and verified successfully." + backup);
                    JOptionPane.showMessageDialog(EveSettingsCopyDialog.this,
                            "EVE settings were copied successfully." + backup,
                            "Settings copied", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Copying EVE settings failed", cause);
                    showError("Copying failed", cause);
                    status.setText("Copying failed. Existing files were left unchanged or restored from backup.\n"
                            + cause.getMessage());
                } finally {
                    copyWorker = null;
                    updateControls();
                }
            }
        };
        copyWorker.execute();
        updateControls();
    }

    private boolean isBusy() {
        return copyWorker != null && !copyWorker.isDone();
    }

    private void clearChoices() {
        updating = true;
        sourceProfile.removeAllItems();
        targetProfile.removeAllItems();
        sourceCharacter.removeAllItems();
        targetCharacter.removeAllItems();
        sourceAccount.removeAllItems();
        targetAccount.removeAllItems();
        updating = false;
    }

    private void showError(String title, Throwable error) {
        JOptionPane.showMessageDialog(this, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }

    private record CharacterChoice(long id, String name, Path path, boolean exists) {
        @Override
        public String toString() {
            return name + " (" + id + ")" + (exists ? "" : " - new settings file");
        }
    }

    private record AccountChoice(SettingsFile file) {
        long id() {
            return file.id();
        }

        @Override
        public String toString() {
            return "Account " + id() + " - modified " + MODIFIED_FORMAT.format(file.modifiedAt());
        }
    }
}
