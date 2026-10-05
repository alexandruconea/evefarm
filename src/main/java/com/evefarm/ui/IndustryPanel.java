package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.BlueprintChoice;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.SolarSystem;
import com.evefarm.service.BuildPlanner.Plan;
import com.evefarm.service.IndustryCalculator;
import com.evefarm.service.IndustryCalculator.Invention;
import com.evefarm.service.IndustryCalculator.MaterialLine;
import com.evefarm.service.IndustryCalculator.Rig;
import com.evefarm.service.IndustryCalculator.Structure;
import com.evefarm.service.IndustryService;
import com.evefarm.ui.column.ColumnCopySupport;
import com.evefarm.ui.column.ColumnVisibilitySupport;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class IndustryPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(IndustryPanel.class.getName());
    private static final String MATERIALS_KEY = "industryMaterials";
    private static final String DECRYPTORS_KEY = "industryDecryptors";
    private static final String BUILD_KEY = "industryBuildOrBuy";
    private static final String SHOPPING_KEY = "industryShopping";
    private static final int SYSTEM_SUGGESTIONS = 12;
    private static final int DEFAULT_COMPONENT_ME = 10;
    private static final int DEFAULT_COMPONENT_TE = 20;

    private final AppContext appContext;
    private final JLabel blueprintLabel = new JLabel("No blueprint chosen");
    private final JButton chooseButton = new JButton("Choose...");
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JSpinner runsSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JSpinner meSpinner = new JSpinner(new SpinnerNumberModel(0, 0, IndustryCalculator.MAX_ME, 1));
    private final JSpinner teSpinner = new JSpinner(new SpinnerNumberModel(0, 0, IndustryCalculator.MAX_TE, 2));
    private final JCheckBox inventionCheck = new JCheckBox("Include invention", true);
    private final JCheckBox buildCheck = new JCheckBox("Build components when cheaper", true);
    private final JTextField systemField = new JTextField(12);
    private final JLabel systemLabel = new JLabel(" ");
    private final JComboBox<Structure> structureCombo =
            new JComboBox<>(Structure.BUILD_SITES.toArray(new Structure[0]));
    private final JComboBox<Rig> materialRigCombo = new JComboBox<>(Rig.values());
    private final JComboBox<Rig> timeRigCombo = new JComboBox<>(Rig.values());
    private final JSpinner taxSpinner = percentSpinner(0);
    private final JLabel salesTaxLabel = new JLabel("-");
    private final JSpinner brokerSpinner = percentSpinner(1.5);
    private final JLabel hintLabel = new JLabel(" ");
    private final IndustryMaterialsTableModel materialsModel = new IndustryMaterialsTableModel();
    private final JTable materialsTable = new JTable(materialsModel);
    private final BuildOrBuyTableModel buildModel = new BuildOrBuyTableModel(this::chooseSource);
    private final JTable buildTable = new JTable(buildModel);
    private final IndustryMaterialsTableModel shoppingModel = new IndustryMaterialsTableModel();
    private final JTable shoppingTable = new JTable(shoppingModel);
    private final JSpinner componentMeSpinner =
            new JSpinner(new SpinnerNumberModel(DEFAULT_COMPONENT_ME, 0, IndustryCalculator.MAX_ME, 1));
    private final JSpinner componentTeSpinner =
            new JSpinner(new SpinnerNumberModel(DEFAULT_COMPONENT_TE, 0, IndustryCalculator.MAX_TE, 2));
    private final Map<Integer, Boolean> buildChoices = new HashMap<>();
    private final DecryptorOptionsTableModel decryptorModel = new DecryptorOptionsTableModel();
    private final JTable decryptorTable = new JTable(decryptorModel);
    private final JPanel inventionPanel = new JPanel(new BorderLayout(0, 4));
    private final JTabbedPane productionTabs = new JTabbedPane();
    private final JButton multibuyButton = new JButton("Copy for Multibuy", Icons.CLIPBOARD);
    private final IndustryResultCard resultCard = new IndustryResultCard();
    private final Timer recalculation = new Timer(400, e -> calculate());
    private final AtomicInteger generation = new AtomicInteger();

    private List<EveCharacter> characters = List.of();
    private String mainCharacterName;
    private BlueprintChoice blueprint;
    private IndustryService.Result result;
    private int shownOption;
    private int ownMe;
    private int ownTe;
    private boolean catalogLoading;
    private boolean loading;

    public IndustryPanel(AppContext appContext) {
        this.appContext = appContext;
        buildUi();
        postInit();
    }

    private static JSpinner percentSpinner(double value) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 0.0, 100.0, 0.1));
        spinner.setPreferredSize(new Dimension(70, spinner.getPreferredSize().height));
        return spinner;
    }

    private void postInit() {
        recalculation.setRepeats(false);
        loading = true;
        try {
            SettingsDao settings = appContext.settingsDao;
            systemField.setText(settings.getOrDefault(SettingsDao.INDUSTRY_SYSTEM, "Jita"));
            settings.getEnum(SettingsDao.INDUSTRY_STRUCTURE, Structure.class)
                    .ifPresent(structureCombo::setSelectedItem);
            settings.getEnum(SettingsDao.INDUSTRY_ME_RIG, Rig.class).ifPresent(materialRigCombo::setSelectedItem);
            settings.getEnum(SettingsDao.INDUSTRY_TE_RIG, Rig.class).ifPresent(timeRigCombo::setSelectedItem);
            taxSpinner.setValue(savedPercent(SettingsDao.INDUSTRY_FACILITY_TAX, 0));
            brokerSpinner.setValue(savedPercent(SettingsDao.INDUSTRY_BROKER_FEE, 1.5));
            buildCheck.setSelected(Boolean.parseBoolean(settings.getOrDefault(SettingsDao.INDUSTRY_BUILD_COMPONENTS,
                    "true")));
            componentMeSpinner.setValue(savedLevel(SettingsDao.INDUSTRY_COMPONENT_ME, DEFAULT_COMPONENT_ME,
                    IndustryCalculator.MAX_ME));
            componentTeSpinner.setValue(savedLevel(SettingsDao.INDUSTRY_COMPONENT_TE, DEFAULT_COMPONENT_TE,
                    IndustryCalculator.MAX_TE));
        } finally {
            loading = false;
        }
        updateStructureControls();

        chooseButton.addActionListener(e -> chooseBlueprint());
        characterCombo.setRenderer(MainCharacterMarks.comboRenderer(() -> mainCharacterName));
        characterCombo.addActionListener(e -> changed());
        meSpinner.addChangeListener(e -> {
            if (!loading) {
                ownMe = (Integer) meSpinner.getValue();
            }
        });
        teSpinner.addChangeListener(e -> {
            if (!loading) {
                ownTe = (Integer) teSpinner.getValue();
            }
        });
        for (JSpinner spinner : List.of(runsSpinner, meSpinner, teSpinner, taxSpinner, brokerSpinner,
                componentMeSpinner, componentTeSpinner)) {
            spinner.addChangeListener(e -> changed());
        }
        inventionCheck.addActionListener(e -> changed());
        buildCheck.addActionListener(e -> {
            buildChoices.clear();
            changed();
        });
        structureCombo.addActionListener(e -> {
            updateStructureControls();
            changed();
        });
        materialRigCombo.addActionListener(e -> changed());
        timeRigCombo.addActionListener(e -> changed());
        SuggestionPopup.install(systemField,
                text -> appContext.industryCatalogService.suggestSystems(text, SYSTEM_SUGGESTIONS),
                SolarSystem::name, IndustryPanel::systemDetail);
        systemField.addActionListener(e -> changed());
        systemField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                changed();
            }
        });
        multibuyButton.addActionListener(e -> copyMultibuy());

        TableStyler.style(materialsTable);
        ColumnVisibilitySupport.install(materialsTable, materialsModel, MATERIALS_KEY,
                appContext.tableColumnStateDao);
        ColumnCopySupport.install(materialsTable, materialsModel);
        TableStyler.style(buildTable);
        ColumnVisibilitySupport.install(buildTable, buildModel, BUILD_KEY, appContext.tableColumnStateDao);
        ColumnCopySupport.install(buildTable, buildModel);
        TableStyler.style(shoppingTable);
        ColumnVisibilitySupport.install(shoppingTable, shoppingModel, SHOPPING_KEY, appContext.tableColumnStateDao);
        ColumnCopySupport.install(shoppingTable, shoppingModel);
        TableStyler.style(decryptorTable);
        decryptorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ColumnVisibilitySupport.install(decryptorTable, decryptorModel, DECRYPTORS_KEY,
                appContext.tableColumnStateDao);
        decryptorTable.getSelectionModel().addListSelectionListener(e -> {
            int row = decryptorTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0 && result != null && row != shownOption) {
                shownOption = row;
                showOption();
            }
        });
        reloadCharacters(true);
    }

    private static String systemDetail(SolarSystem system) {
        String security = String.format(Locale.US, "%.1f", system.roundedSecurity());
        return system.regionName() == null ? security : security + "  ·  " + system.regionName();
    }

    private int savedLevel(String key, int fallback, int max) {
        try {
            return Math.clamp(Integer.parseInt(appContext.settingsDao.getOrDefault(key, String.valueOf(fallback))), 0,
                    max);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double savedPercent(String key, double fallback) {
        try {
            return Double.parseDouble(appContext.settingsDao.getOrDefault(key, String.valueOf(fallback)));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public void onShown() {
        reloadCharacters(false);
        loadCatalog();
    }

    public void refreshCharacterFilter() {
        reloadCharacters(true);
    }

    private void reloadCharacters(boolean selectMain) {
        characters = appContext.characterService.listCharactersMainFirst();
        Long mainId = appContext.characterService.mainCharacterId().orElse(null);
        mainCharacterName = characters.stream()
                .filter(character -> mainId != null && character.characterId() == mainId)
                .map(EveCharacter::characterName)
                .findFirst().orElse(null);
        String previous = (String) characterCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        characters.forEach(character -> model.addElement(character.characterName()));
        loading = true;
        try {
            characterCombo.setModel(model);
            if (!selectMain && previous != null && model.getIndexOf(previous) >= 0) {
                characterCombo.setSelectedItem(previous);
            } else if (model.getSize() > 0) {
                characterCombo.setSelectedIndex(0);
            }
        } finally {
            loading = false;
        }
    }

    private void loadCatalog() {
        if (catalogLoading) {
            return;
        }
        catalogLoading = true;
        chooseButton.setEnabled(false);
        boolean downloading = appContext.industryCatalogService.isStale();
        if (downloading) {
            hintLabel.setText("Downloading EVE's blueprint and solar system data (about 27 MB, once a month)...");
        }
        new SwingWorker<List<BlueprintChoice>, Void>() {
            @Override
            protected List<BlueprintChoice> doInBackground() {
                appContext.industryCatalogService.refreshIfStale();
                appContext.industryCatalogService.solarSystems();
                return appContext.industryCatalogService.manufacturingChoices();
            }

            @Override
            protected void done() {
                catalogLoading = false;
                chooseButton.setEnabled(true);
                try {
                    List<BlueprintChoice> choices = get();
                    if (downloading) {
                        hintLabel.setText(" ");
                    }
                    if (blueprint == null) {
                        restoreBlueprint(choices);
                    }
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load the industry data", e);
                    hintLabel.setText("Couldn't download EVE's blueprint data - see the log. It is tried again when "
                            + "you open this tab.");
                }
            }
        }.execute();
    }

    private void restoreBlueprint(List<BlueprintChoice> choices) {
        String saved = appContext.settingsDao.getOrDefault(SettingsDao.INDUSTRY_BLUEPRINT, "");
        choices.stream().filter(choice -> String.valueOf(choice.blueprintId()).equals(saved)).findFirst()
                .ifPresent(this::setBlueprint);
    }

    private void chooseBlueprint() {
        BlueprintChoice choice = new BlueprintChooserDialog(SwingUtilities.getWindowAncestor(this),
                appContext.industryCatalogService.manufacturingChoices()).choose();
        if (choice != null) {
            setBlueprint(choice);
            appContext.settingsDao.set(SettingsDao.INDUSTRY_BLUEPRINT, String.valueOf(choice.blueprintId()));
        }
    }

    private void setBlueprint(BlueprintChoice choice) {
        blueprint = choice;
        buildChoices.clear();
        blueprintLabel.setText(choice.productName());
        blueprintLabel.setIcon(null);
        appContext.itemIconService.loadBlueprintAsync(choice.blueprintId(), icon -> {
            if (blueprint == choice) {
                blueprintLabel.setIcon(icon);
            }
        });
        changed();
    }

    private void chooseSource(int typeId, boolean build) {
        buildChoices.put(typeId, build);
        changed();
    }

    private void updateStructureControls() {
        boolean structure = structureCombo.getSelectedItem() != Structure.NPC_STATION;
        materialRigCombo.setEnabled(structure);
        timeRigCombo.setEnabled(structure);
        taxSpinner.setEnabled(structure);
    }

    private void changed() {
        if (!loading) {
            recalculation.restart();
        }
    }

    private IndustryService.Settings settings() {
        Object name = characterCombo.getSelectedItem();
        Long characterId = characters.stream().filter(character -> character.characterName().equals(name))
                .map(EveCharacter::characterId).findFirst().orElse(null);
        return new IndustryService.Settings(blueprint.blueprintId(), characterId, (Integer) runsSpinner.getValue(),
                ownMe, ownTe, inventionCheck.isSelected(),
                systemField.getText(), (Structure) structureCombo.getSelectedItem(),
                (Rig) materialRigCombo.getSelectedItem(), (Rig) timeRigCombo.getSelectedItem(),
                percent(taxSpinner), percent(brokerSpinner), buildCheck.isSelected(),
                (Integer) componentMeSpinner.getValue(), (Integer) componentTeSpinner.getValue(),
                Map.copyOf(buildChoices));
    }

    private static double percent(JSpinner spinner) {
        return ((Number) spinner.getValue()).doubleValue() / 100;
    }

    private void saveSettings() {
        SettingsDao settings = appContext.settingsDao;
        settings.set(SettingsDao.INDUSTRY_SYSTEM, systemField.getText().strip());
        settings.set(SettingsDao.INDUSTRY_STRUCTURE, ((Structure) structureCombo.getSelectedItem()).name());
        settings.set(SettingsDao.INDUSTRY_ME_RIG, ((Rig) materialRigCombo.getSelectedItem()).name());
        settings.set(SettingsDao.INDUSTRY_TE_RIG, ((Rig) timeRigCombo.getSelectedItem()).name());
        settings.set(SettingsDao.INDUSTRY_FACILITY_TAX, String.valueOf(taxSpinner.getValue()));
        settings.set(SettingsDao.INDUSTRY_BROKER_FEE, String.valueOf(brokerSpinner.getValue()));
        settings.set(SettingsDao.INDUSTRY_BUILD_COMPONENTS, String.valueOf(buildCheck.isSelected()));
        settings.set(SettingsDao.INDUSTRY_COMPONENT_ME, String.valueOf(componentMeSpinner.getValue()));
        settings.set(SettingsDao.INDUSTRY_COMPONENT_TE, String.valueOf(componentTeSpinner.getValue()));
    }

    private void calculate() {
        saveSettings();
        if (blueprint == null) {
            return;
        }
        IndustryService.Settings settings = settings();
        int run = generation.incrementAndGet();
        hintLabel.setText("Calculating...");
        new SwingWorker<IndustryService.Result, Void>() {
            @Override
            protected IndustryService.Result doInBackground() {
                return appContext.industryService.calculate(settings);
            }

            @Override
            protected void done() {
                if (run != generation.get()) {
                    return;
                }
                try {
                    showResult(get());
                } catch (Exception e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    LOG.log(Level.WARNING, "Failed to calculate the industry job", cause);
                    hintLabel.setText("Couldn't calculate: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void showResult(IndustryService.Result calculated) {
        result = calculated;
        shownOption = calculated.bestIndex();
        SolarSystem system = calculated.system();
        systemLabel.setText(system == null ? "System not found, cost index 0"
                : String.format(Locale.US, "%.1f  ·  cost index %.2f%%", system.roundedSecurity(),
                calculated.manufacturingIndex() * 100));
        List<String> notes = new ArrayList<>();
        IndustryService.SkillEffects skills = calculated.skills();
        if (skills.assumed()) {
            notes.add("Skills are assumed at level IV - run Update > Skills for this character's real skills.");
        }
        if (!skills.canBuild()) {
            notes.add("This character lacks a skill needed to build it.");
        }
        if (!skills.canInvent()) {
            notes.add("This character lacks a skill needed to invent it.");
        }
        if (calculated.productPrice() <= 0) {
            notes.add("No market price for the product.");
        }
        if (buildCheck.isSelected() && !calculated.reactionsAllowed()) {
            notes.add("Reactions need low-sec or null-sec, so their products are bought.");
        }
        hintLabel.setText(notes.isEmpty() ? " " : String.join("  ", notes));
        salesTaxLabel.setText(String.format(Locale.US, "%.2f%%", skills.salesTax() * 100));
        showInventionTab(calculated.invented());
        inventionCheck.setEnabled(calculated.inventable());
        decryptorModel.setRows(calculated.invented() ? calculated.options() : List.of());
        TableStyler.packColumns(decryptorTable);
        if (calculated.invented()) {
            decryptorTable.setRowSelectionInterval(shownOption, shownOption);
        }
        showOption();
    }

    private void showInventionTab(boolean shown) {
        int index = productionTabs.indexOfComponent(inventionPanel);
        if (shown && index < 0) {
            productionTabs.addTab("Invention", inventionPanel);
        } else if (!shown && index >= 0) {
            productionTabs.removeTabAt(index);
        }
    }

    private void showOption() {
        IndustryService.Option option = result.options().get(shownOption);
        Plan plan = option.plan();
        List<IndustryMaterialsTableModel.Row> rows = new ArrayList<>();
        for (MaterialLine line : plan.materials()) {
            String source = plan.component(line.typeId()).map(component -> component.built() ? "Build" : "Buy")
                    .orElse("");
            rows.add(new IndustryMaterialsTableModel.Row(line.typeId(), name(line.typeId()), line.quantity(),
                    line.unitPrice(), line.total(), source));
        }
        materialsModel.setRows(rows);
        TableStyler.packColumns(materialsTable);
        buildModel.setRows(plan.components().stream()
                .map(component -> new BuildOrBuyTableModel.Row(component.typeId(), name(component.typeId()),
                        component.reaction(), component.needed(), component.runs(), component.marketPrice(),
                        component.buildPrice(), component.saving(), component.built(), component.time()))
                .toList());
        TableStyler.packColumns(buildTable);
        shoppingModel.setRows(plan.shopping().stream()
                .map(line -> new IndustryMaterialsTableModel.Row(line.typeId(), name(line.typeId()), line.quantity(),
                        line.unitPrice(), line.total(), ""))
                .toList());
        TableStyler.packColumns(shoppingTable);
        showEfficiency(option.invention());
        resultCard.show(result, option);
    }

    private String name(int typeId) {
        return result.names().getOrDefault(typeId, "Type #" + typeId);
    }

    private void showEfficiency(Invention invention) {
        loading = true;
        try {
            meSpinner.setValue(invention == null ? ownMe : invention.me());
            teSpinner.setValue(invention == null ? ownTe : invention.te());
        } finally {
            loading = false;
        }
        String tip = invention == null ? null : String.format(Locale.US,
                "The invented copy has ME %d and TE %d, plus the decryptor's bonus. Clear Include invention to "
                        + "set your own.", IndustryCalculator.INVENTED_BASE_ME, IndustryCalculator.INVENTED_BASE_TE);
        for (JSpinner spinner : List.of(meSpinner, teSpinner)) {
            spinner.setEnabled(invention == null);
            spinner.setToolTipText(tip);
        }
    }

    private void copyMultibuy() {
        StringBuilder text = new StringBuilder();
        for (IndustryMaterialsTableModel.Row row : shoppingModel.rows()) {
            text.append(row.name()).append('\t').append(row.quantity()).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        hintLabel.setText("Copied the " + shoppingModel.getRowCount()
                + " items of the shopping list - paste them into Multibuy in EVE.");
    }

    private void buildUi() {
        setLayout(new BorderLayout());
        blueprintLabel.setFont(blueprintLabel.getFont().deriveFont(Font.BOLD,
                blueprintLabel.getFont().getSize2D() + 2f));
        blueprintLabel.putClientProperty("html.disable", Boolean.TRUE);
        blueprintLabel.setIconTextGap(10);
        runsSpinner.setPreferredSize(new Dimension(90, runsSpinner.getPreferredSize().height));
        inventionCheck.setOpaque(false);
        inventionCheck.setToolTipText("For Tech II blueprints: count the cost of inventing the blueprint copies");
        buildCheck.setOpaque(false);
        buildCheck.setToolTipText("Make the components and reaction products that cost less to build than to buy. "
                + "Change any of them in the Build or buy tab");
        componentMeSpinner.setToolTipText("The ME of your component blueprints");
        componentTeSpinner.setToolTipText("The TE of your component blueprints");
        multibuyButton.setToolTipText("Copies the shopping list: everything you need to buy");
        systemLabel.setToolTipText("The system's security status and manufacturing cost index");
        materialRigCombo.setToolTipText("The structure's material efficiency rig for this kind of item");
        timeRigCombo.setToolTipText("The structure's time efficiency rig for this kind of item");
        salesTaxLabel.setToolTipText("7.5% less 11% for each level of Accounting");
        brokerSpinner.setToolTipText("NPC stations: 3% less 0.3% for each level of Broker Relations and less with "
                + "standings. Set it to 0 if you sell to buy orders");

        JPanel blueprintHeader = new JPanel(new BorderLayout(12, 0));
        blueprintHeader.setOpaque(false);
        blueprintHeader.add(blueprintLabel, BorderLayout.CENTER);
        JPanel chooseHolder = new JPanel(new GridBagLayout());
        chooseHolder.setOpaque(false);
        chooseHolder.add(chooseButton);
        blueprintHeader.add(chooseHolder, BorderLayout.EAST);
        JPanel blueprintForm = new JPanel(new GridBagLayout());
        place(blueprintForm, blueprintHeader, 0, 0, 6);
        field(blueprintForm, "Character", characterCombo, 0, 1, 5);
        field(blueprintForm, "Runs", runsSpinner, 0, 2, 1);
        field(blueprintForm, "ME", meSpinner, 2, 2, 1);
        field(blueprintForm, "TE", teSpinner, 4, 2, 1);
        place(blueprintForm, inventionCheck, 0, 3, 6);
        place(blueprintForm, buildCheck, 0, 4, 6);

        JPanel facilityForm = new JPanel(new GridBagLayout());
        field(facilityForm, "System", systemField, 0, 0, 1);
        place(facilityForm, systemLabel, 2, 0, 2);
        field(facilityForm, "Built in", structureCombo, 0, 1, 1);
        field(facilityForm, "Facility tax %", taxSpinner, 2, 1, 1);
        field(facilityForm, "Material rig", materialRigCombo, 0, 2, 1);
        field(facilityForm, "Time rig", timeRigCombo, 2, 2, 1);

        JPanel sellingForm = new JPanel(new GridBagLayout());
        field(sellingForm, "Broker fee %", brokerSpinner, 0, 0, 1);
        field(sellingForm, "Sales tax", salesTaxLabel, 0, 1, 1);

        JPanel cards = new JPanel(new GridBagLayout());
        cards.setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        section(cards, "Blueprint", blueprintForm, 0);
        section(cards, "Facility", facilityForm, 1);
        section(cards, "Selling", sellingForm, 2);
        JPanel resultSection = new JPanel(new BorderLayout(0, 6));
        resultSection.add(bold(new JLabel("Result")), BorderLayout.NORTH);
        resultSection.add(resultCard.component(), BorderLayout.CENTER);
        GridBagConstraints resultCell = new GridBagConstraints();
        resultCell.gridx = 3;
        resultCell.weightx = 1;
        resultCell.fill = GridBagConstraints.BOTH;
        resultCell.anchor = GridBagConstraints.NORTHWEST;
        cards.add(resultSection, resultCell);

        hintLabel.setBorder(BorderFactory.createEmptyBorder(6, 10, 2, 8));
        JPanel top = new JPanel(new BorderLayout());
        top.add(cards, BorderLayout.CENTER);
        top.add(hintLabel, BorderLayout.SOUTH);

        JPanel buildHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        buildHeader.add(UiColors.mutedLabel("Component blueprints"));
        buildHeader.add(UiColors.mutedLabel("ME"));
        buildHeader.add(componentMeSpinner);
        buildHeader.add(UiColors.mutedLabel("TE"));
        buildHeader.add(componentTeSpinner);
        buildHeader.add(UiColors.mutedLabel("   Tick Build to make an item yourself."));
        JPanel buildPanel = new JPanel(new BorderLayout(0, 4));
        buildPanel.add(buildHeader, BorderLayout.NORTH);
        buildPanel.add(new JScrollPane(buildTable), BorderLayout.CENTER);
        JPanel shoppingHeader = new JPanel(new BorderLayout());
        shoppingHeader.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 2));
        shoppingHeader.add(UiColors.mutedLabel("Everything you still need to buy."), BorderLayout.WEST);
        shoppingHeader.add(multibuyButton, BorderLayout.EAST);
        JPanel shoppingPanel = new JPanel(new BorderLayout(0, 4));
        shoppingPanel.add(shoppingHeader, BorderLayout.NORTH);
        shoppingPanel.add(new JScrollPane(shoppingTable), BorderLayout.CENTER);
        JLabel inventionHint = UiColors.mutedLabel("Click a decryptor to see its materials and numbers. The most profitable "
                + "one is chosen for you.");
        inventionHint.setBorder(BorderFactory.createEmptyBorder(4, 8, 2, 2));
        inventionPanel.add(inventionHint, BorderLayout.NORTH);
        inventionPanel.add(new JScrollPane(decryptorTable), BorderLayout.CENTER);
        productionTabs.addTab("Materials", new JScrollPane(materialsTable));
        productionTabs.addTab("Build or buy", buildPanel);
        productionTabs.addTab("Shopping list", shoppingPanel);
        JPanel production = new JPanel(new BorderLayout());
        production.setBorder(BorderFactory.createEmptyBorder(2, 8, 8, 8));
        production.add(productionTabs, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(production, BorderLayout.CENTER);
    }

    private static JLabel bold(JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        return label;
    }

    private static void section(JPanel cards, String title, JPanel form, int column) {
        form.setOpaque(false);
        CardPanel card = new CardPanel(new BorderLayout());
        card.add(form, BorderLayout.NORTH);
        JPanel section = new JPanel(new BorderLayout(0, 6));
        section.add(bold(new JLabel(title)), BorderLayout.NORTH);
        section.add(card, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(0, 0, 0, 12);
        cards.add(section, c);
    }

    private static void field(JPanel form, String label, JComponent component, int column, int row, int width) {
        place(form, UiColors.mutedLabel(label), column, row, 1);
        place(form, component, column + 1, row, width);
    }

    private static void place(JPanel form, JComponent component, int column, int row, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.gridwidth = width;
        c.anchor = GridBagConstraints.WEST;
        c.fill = fillsItsCell(component) ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.insets = new Insets(5, 0, 5, 10);
        form.add(component, c);
    }

    private static boolean fillsItsCell(JComponent component) {
        return component instanceof JComboBox || component instanceof JTextField || component instanceof JPanel;
    }
}
