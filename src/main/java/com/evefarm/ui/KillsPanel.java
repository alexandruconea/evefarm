package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.KillDayTypeRow;
import com.evefarm.service.KillsReportService;
import com.evefarm.util.DateUtil;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class KillsPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(KillsPanel.class.getName());
    private static final Dimension FIELD_SIZE = new Dimension(220, 26);
    private static final String ALL_SYSTEMS = "All Systems";

    private final AppContext appContext;
    private final DefaultListModel<EveCharacter> characterListModel = new DefaultListModel<>();
    private Long mainCharacterId;
    private final DefaultComboBoxModel<String> factionComboModel = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> systemComboModel = new DefaultComboBoxModel<>();
    private final Map<String, JCheckBox> shipTypeCheckBoxes = new LinkedHashMap<>();
    private final AtomicInteger reloadGeneration = new AtomicInteger(0);

    private final JList<EveCharacter> characterList = new JList<>();
    private final JCheckBox allProfilesCheckBox = new JCheckBox("All Profiles", true);
    private final JComboBox<String> quickDateCombo = new JComboBox<>();
    private final JSpinner fromSpinner = new JSpinner();
    private final JSpinner toSpinner = new JSpinner();
    private final JComboBox<String> factionCombo = new JComboBox<>();
    private final JComboBox<String> systemCombo = new JComboBox<>();
    private final JPanel shipTypePanel = new JPanel();
    private final JButton showButton = new JButton("Show");
    private final JButton exportPdfButton = new JButton("Export PDF...");

    private final KillsTableModel killsTableModel = new KillsTableModel();
    private final JTable killsTable = new JTable(killsTableModel);

    private boolean populatingFactionOptions;
    private boolean populatingSystemOptions;

    public KillsPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        quickDateCombo.setSelectedItem("1 Month");
        refreshCharacterFilter();
    }

    private void buildUi() {
        setLayout(new BorderLayout());

        killsTable.setRowSorter(new TableRowSorter<>(killsTableModel));
        TableStyler.style(killsTable);

        add(new JScrollPane(killsTable), BorderLayout.CENTER);
        add(sidebar(), BorderLayout.EAST);
    }

    private JPanel sidebar() {
        showButton.setIcon(Icons.EYE);
        showButton.addActionListener(e -> reloadTable());
        exportPdfButton.setIcon(Icons.DOCUMENT);
        exportPdfButton.addActionListener(e -> exportPdf());

        quickDateCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                "1 Day", "1 Week", "2 Weeks", "1 Month", "3 Months", "6 Months", "1 Year", "2 Years"}));
        quickDateCombo.addActionListener(e -> applyQuickDate((String) quickDateCombo.getSelectedItem()));
        fromSpinner.setModel(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        toSpinner.setModel(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));

        factionCombo.setModel(factionComboModel);
        factionCombo.addActionListener(e -> {
            if (!populatingFactionOptions) {
                reloadSystemOptions(reloadGeneration.incrementAndGet());
            }
        });

        systemCombo.setModel(systemComboModel);
        systemCombo.addActionListener(e -> {
            if (!populatingSystemOptions) {
                reloadShipTypeOptions(reloadGeneration.incrementAndGet());
            }
        });

        shipTypePanel.setLayout(new BoxLayout(shipTypePanel, BoxLayout.Y_AXIS));
        JScrollPane shipTypeScrollPane = new JScrollPane(shipTypePanel);
        shipTypeScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        shipTypeScrollPane.setPreferredSize(new Dimension(220, 140));

        allProfilesCheckBox.addActionListener(e -> {
            characterList.setEnabled(!allProfilesCheckBox.isSelected());
            reloadFactionOptions();
        });
        characterList.setModel(characterListModel);
        characterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        characterList.setEnabled(false);
        characterList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.characterName());
            label.setIcon(MainCharacterMarks.iconFor(mainCharacterId, value.characterId()));
            label.setOpaque(true);
            label.setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            label.setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return label;
        });
        characterList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                reloadFactionOptions();
            }
        });
        JScrollPane characterScrollPane = new JScrollPane(characterList);
        characterScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);
        characterScrollPane.setPreferredSize(new Dimension(220, 140));

        fixWidth(quickDateCombo);
        fixWidth(fromSpinner);
        fixWidth(toSpinner);
        fixWidth(showButton);
        fixWidth(exportPdfButton);
        fixWidth(factionCombo);
        fixWidth(systemCombo);

        JPanel sidebarPanel = new JPanel();
        sidebarPanel.setLayout(new BoxLayout(sidebarPanel, BoxLayout.Y_AXIS));
        sidebarPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        sidebarPanel.add(labeled("Quick Date", quickDateCombo));
        sidebarPanel.add(Box.createVerticalStrut(8));
        sidebarPanel.add(labeled("From", fromSpinner));
        sidebarPanel.add(Box.createVerticalStrut(8));
        sidebarPanel.add(labeled("To", toSpinner));
        sidebarPanel.add(Box.createVerticalStrut(8));
        addLeftAligned(sidebarPanel, showButton);
        sidebarPanel.add(Box.createVerticalStrut(4));
        addLeftAligned(sidebarPanel, exportPdfButton);
        sidebarPanel.add(Box.createVerticalStrut(12));
        sidebarPanel.add(labeled("Faction", factionCombo));
        sidebarPanel.add(Box.createVerticalStrut(8));
        sidebarPanel.add(labeled("System", systemCombo));
        sidebarPanel.add(Box.createVerticalStrut(8));
        sidebarPanel.add(labeled("Ship Types", shipTypeScrollPane));
        sidebarPanel.add(Box.createVerticalStrut(12));
        addLeftAligned(sidebarPanel, new JLabel("Characters"));
        sidebarPanel.add(Box.createVerticalStrut(4));
        addLeftAligned(sidebarPanel, allProfilesCheckBox);
        sidebarPanel.add(Box.createVerticalStrut(4));
        sidebarPanel.add(characterScrollPane);
        characterScrollPane.setAlignmentX(Component.LEFT_ALIGNMENT);

        return sidebarPanel;
    }

    private static JPanel labeled(String text, JComponent component) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(text);
        addLeftAligned(panel, label);
        panel.add(Box.createVerticalStrut(4));
        addLeftAligned(panel, component);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static void addLeftAligned(JPanel panel, JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(component);
    }

    private static void fixWidth(JComponent component) {
        component.setMaximumSize(FIELD_SIZE);
        component.setPreferredSize(FIELD_SIZE);
    }

    public void onShown() {
        reloadFactionOptions();
    }

    public void refreshCharacterFilter() {
        characterListModel.clear();
        mainCharacterId = appContext.characterService.mainCharacterId().orElse(null);
        for (EveCharacter character : appContext.characterService.listCharactersMainFirst()) {
            characterListModel.addElement(character);
        }
        if (!characterListModel.isEmpty()) {
            characterList.setSelectionInterval(0, characterListModel.size() - 1);
        }
        reloadFactionOptions();
    }

    private void applyQuickDate(String option) {
        LocalDate today = LocalDate.now();
        LocalDate from = switch (option) {
            case "1 Day" -> today.minusDays(1);
            case "1 Week" -> today.minusWeeks(1);
            case "2 Weeks" -> today.minusWeeks(2);
            case "3 Months" -> today.minusMonths(3);
            case "6 Months" -> today.minusMonths(6);
            case "1 Year" -> today.minusYears(1);
            case "2 Years" -> today.minusYears(2);
            default -> today.minusMonths(1);
        };
        fromSpinner.setValue(DateUtil.toDate(from));
        toSpinner.setValue(DateUtil.toDate(today));
        reloadTable();
    }

    private Set<Long> selectedCharacterIds() {
        if (allProfilesCheckBox.isSelected()) {
            return appContext.characterService.listCharacters().stream()
                    .map(EveCharacter::characterId)
                    .collect(Collectors.toSet());
        }
        return characterList.getSelectedValuesList().stream()
                .map(EveCharacter::characterId)
                .collect(Collectors.toSet());
    }

    private Set<String> selectedFactionLabels() {
        String selected = (String) factionCombo.getSelectedItem();
        return selected == null ? Set.of() : Set.of(selected);
    }

    private String selectedSystemFilter() {
        String selected = (String) systemCombo.getSelectedItem();
        return (selected == null || ALL_SYSTEMS.equals(selected)) ? null : selected;
    }

    private Set<String> selectedNpcNames() {
        return shipTypeCheckBoxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void reloadFactionOptions() {
        int generation = reloadGeneration.incrementAndGet();
        Set<Long> characterIds = selectedCharacterIds();
        String previouslySelected = (String) factionCombo.getSelectedItem();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return appContext.killService.listFactionOptions(characterIds);
            }

            @Override
            protected void done() {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                try {
                    applyFactionOptions(get(), previouslySelected, generation);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load kill factions", e);
                }
            }
        }.execute();
    }

    private void applyFactionOptions(List<String> factions, String previouslySelected, int generation) {
        populatingFactionOptions = true;
        try {
            factionComboModel.removeAllElements();
            for (String faction : factions) {
                factionComboModel.addElement(faction);
            }
            if (previouslySelected != null && factions.contains(previouslySelected)) {
                factionComboModel.setSelectedItem(previouslySelected);
            } else if (!factions.isEmpty()) {
                factionComboModel.setSelectedItem(factions.get(0));
            }
        } finally {
            populatingFactionOptions = false;
        }
        reloadSystemOptions(generation);
    }

    private void reloadSystemOptions(int generation) {
        Set<Long> characterIds = selectedCharacterIds();
        Set<String> factionLabels = selectedFactionLabels();
        String previouslySelected = (String) systemCombo.getSelectedItem();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return appContext.killService.listSystemOptions(characterIds, factionLabels);
            }

            @Override
            protected void done() {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                try {
                    applySystemOptions(get(), previouslySelected, generation);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load kill systems", e);
                }
            }
        }.execute();
    }

    private void applySystemOptions(List<String> systems, String previouslySelected, int generation) {
        populatingSystemOptions = true;
        try {
            systemComboModel.removeAllElements();
            systemComboModel.addElement(ALL_SYSTEMS);
            for (String system : systems) {
                systemComboModel.addElement(system);
            }
            if (previouslySelected != null && (ALL_SYSTEMS.equals(previouslySelected) || systems.contains(previouslySelected))) {
                systemComboModel.setSelectedItem(previouslySelected);
            } else {
                systemComboModel.setSelectedItem(ALL_SYSTEMS);
            }
        } finally {
            populatingSystemOptions = false;
        }
        reloadShipTypeOptions(generation);
    }

    private void reloadShipTypeOptions(int generation) {
        Set<Long> characterIds = selectedCharacterIds();
        Set<String> factionLabels = selectedFactionLabels();
        String systemFilter = selectedSystemFilter();

        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return appContext.killService.listShipTypeOptions(characterIds, factionLabels, systemFilter);
            }

            @Override
            protected void done() {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                try {
                    applyShipTypeOptions(get(), generation);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load kill ship types", e);
                }
            }
        }.execute();
    }

    private void applyShipTypeOptions(List<String> npcNames, int generation) {
        shipTypePanel.removeAll();
        shipTypeCheckBoxes.clear();
        for (String npcName : npcNames) {
            JCheckBox checkBox = new JCheckBox(npcName, true);
            checkBox.addActionListener(e -> reloadTable());
            shipTypeCheckBoxes.put(npcName, checkBox);
            shipTypePanel.add(checkBox);
        }
        shipTypePanel.revalidate();
        shipTypePanel.repaint();
        reloadTable(generation);
    }

    private void reloadTable() {
        reloadTable(reloadGeneration.incrementAndGet());
    }

    void refreshKills() {
        reloadTable();
    }

    private void reloadTable(int generation) {
        Set<Long> characterIds = selectedCharacterIds();
        Set<String> factionLabels = selectedFactionLabels();
        Set<String> npcNames = selectedNpcNames();
        String systemFilter = selectedSystemFilter();
        Instant from = DateUtil.startOfDay(DateUtil.toLocalDate((Date) fromSpinner.getValue()));
        Instant to = DateUtil.endOfDay(DateUtil.toLocalDate((Date) toSpinner.getValue()));

        new SwingWorker<List<KillDayTypeRow>, Void>() {
            @Override
            protected List<KillDayTypeRow> doInBackground() {
                return appContext.killService.listDayTypeRows(
                        characterIds, factionLabels, npcNames, systemFilter, from, to);
            }

            @Override
            protected void done() {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                try {
                    killsTableModel.setRows(get());
                    TableStyler.packColumns(killsTable);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to reload kills table", e);
                }
            }
        }.execute();
    }

    private String selectedCharacterLabel() {
        if (allProfilesCheckBox.isSelected()) {
            return "All Profiles";
        }
        List<String> names = characterList.getSelectedValuesList().stream()
                .map(EveCharacter::characterName)
                .toList();
        return names.isEmpty() ? "No characters selected" : String.join(", ", names);
    }

    private void exportPdf() {
        List<KillDayTypeRow> rows = killsTableModel.rows();

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Kills Report");
        String faction = (String) factionCombo.getSelectedItem();
        String slug = faction == null ? "kills" : faction.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        chooser.setSelectedFile(new File("kills-report-" + (slug.isBlank() ? "all" : slug) + ".pdf"));
        chooser.setFileFilter(new FileNameExtensionFilter("PDF document (*.pdf)", "pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            target = new File(target.getParentFile(), target.getName() + ".pdf");
        }
        File finalTarget = target;

        KillsReportService.ReportFilters filters = new KillsReportService.ReportFilters(
                selectedCharacterLabel(),
                faction == null ? "(no faction)" : faction,
                (String) systemCombo.getSelectedItem(),
                DateUtil.toLocalDate((Date) fromSpinner.getValue()),
                DateUtil.toLocalDate((Date) toSpinner.getValue()));

        exportPdfButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                appContext.killsReportService.generateReport(finalTarget.toPath(), filters, rows);
                return null;
            }

            @Override
            protected void done() {
                exportPdfButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(KillsPanel.this,
                            "Report saved to:\n" + finalTarget,
                            "Export Kills Report", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to generate kills report", e);
                    JOptionPane.showMessageDialog(KillsPanel.this,
                            "Failed to generate the report: " + e.getCause(),
                            "Export Kills Report", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
