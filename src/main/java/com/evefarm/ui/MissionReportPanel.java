package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.LpGain;
import com.evefarm.model.MissionAgent;
import com.evefarm.service.MissionIncome;
import com.evefarm.service.MissionIncome.Grouping;
import com.evefarm.service.MissionIncome.Payment;
import com.evefarm.service.MissionIncome.Period;
import com.evefarm.service.MissionIncome.Report;
import com.evefarm.ui.EveEntity.Kind;
import com.evefarm.ui.MissionAgentsTableModel.Row;
import com.evefarm.ui.column.ColumnTableModel;
import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class MissionReportPanel extends TabPanel {

    private static final String ALL_CHARACTERS = "All characters";
    private static final String SAVE_TITLE = "Save Mission Report";
    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US);
    private static final double DEFAULT_DIVIDER = 0.6;
    private static final String WIDEST_PERIOD = "Wed, Dec 30, 2026 to Tue, Jan 5, 2027";
    private static final String WIDEST_LABEL = "Other bounties near a turn-in";
    private static final String WIDEST_ISK = "00,000,000,000.00 ISK";
    private static final int CARD_INSET = 15;
    private static final int CARD_PADDING = 6;
    private static final int CARD_GAP = 8;
    private static final float HEADLINE_GROWTH = 8f;
    private static final int VALUE_GAP = 24;

    private record Loaded(List<Payment> payments, List<LpGain> gains, Map<Long, Instant> lpFirstRecorded,
                          double targetIskPerLp, Map<String, MissionAgent> agents) {
    }

    private final AppContext appContext;
    private final Consumer<StandingTarget> showStandings;
    private final ZoneId zone = ZoneId.systemDefault();
    private final DayOfWeek firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
    private final JComboBox<Period> periodCombo = new JComboBox<>(Period.values());
    private final JButton previousButton = new JButton(Icons.PREVIOUS);
    private final JButton nextButton = new JButton(Icons.NEXT);
    private final JButton todayButton = new JButton("Today");
    private final JLabel periodLabel = new JLabel(" ");
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JButton saveButton = new JButton("Save Report...", Icons.SAVE);
    private final JPanel summaryPanel = new JPanel(new GridBagLayout());
    private final JLabel daysTitle = new JLabel("By day");
    private final MissionDaysTableModel daysModel = new MissionDaysTableModel();
    private final JTable daysTable = new JTable(daysModel);
    private final MissionAgentsTableModel agentsModel = new MissionAgentsTableModel();
    private final JTable agentsTable = new JTable(agentsModel);
    private final AtomicInteger generation = new AtomicInteger();
    private Loaded loaded = new Loaded(List.of(), List.of(), Map.of(), 0, Map.of());
    private Period period;
    private LocalDate from;
    private Report report;
    private List<String> notes = List.of();
    private boolean updatingFilters;
    private int contentWidth;

    public MissionReportPanel(AppContext appContext, Consumer<StandingTarget> showStandings) {
        super(new BorderLayout());
        this.appContext = appContext;
        this.showStandings = showStandings;
        period = appContext.settingsDao.getEnum(SettingsDao.MISSION_REPORT_PERIOD, Period.class).orElse(Period.WEEK);
        from = period.start(LocalDate.now(zone), firstDayOfWeek);
        buildUi();
        appContext.loyaltyPointService.addChangeListener(() -> SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                onShown();
            }
        }));
        appContext.agentImportService.addImportListener(() -> SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                onShown();
            }
        }));
    }

    public void onShown() {
        appContext.agentImportService.importInBackgroundIfMissing();
        BackgroundLoader.load(generation, () -> new Loaded(
                        MissionIncome.classify(appContext.walletJournalDao.findMissionIncome(),
                                appContext.npcCatalogService.catalog()::isMissionNpc),
                        appContext.loyaltyPointService.gains(), appContext.loyaltyPointService.firstRecorded(),
                        appContext.settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP,
                                SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP),
                        appContext.walletJournalDao.findMissionAgents().stream().collect(Collectors.toMap(
                                MissionAgent::agentName, agent -> agent, (first, second) -> first))),
                this::apply, "the mission report", failure -> {
                    summaryPanel.removeAll();
                    summaryPanel.add(UiColors.mutedLabel("Couldn't load the mission report - see the log."));
                    summaryPanel.revalidate();
                });
    }

    private void buildUi() {
        periodCombo.setSelectedItem(period);
        periodCombo.addActionListener(e -> periodChanged());
        ComboSizing.options(periodCombo);
        previousButton.addActionListener(e -> move(-1));
        nextButton.addActionListener(e -> move(1));
        todayButton.addActionListener(e -> {
            from = period.start(LocalDate.now(zone), firstDayOfWeek);
            showReport();
        });
        characterCombo.addActionListener(e -> {
            if (!updatingFilters) {
                showReport();
            }
        });
        ComboSizing.names(characterCombo);
        ComboFilter.install(characterCombo);
        saveButton.addActionListener(e -> saveReport());
        saveButton.setToolTipText("Save this report as a text file");

        for (JButton arrow : List.of(previousButton, nextButton)) {
            arrow.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        }
        periodLabel.setFont(periodLabel.getFont().deriveFont(Font.BOLD));
        periodLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JPanel controls = controls();
        FontMetrics plain = periodLabel.getFontMetrics(periodLabel.getFont().deriveFont(Font.PLAIN));
        FontMetrics bold = periodLabel.getFontMetrics(periodLabel.getFont().deriveFont(Font.BOLD,
                periodLabel.getFont().getSize2D() + 1f));
        contentWidth = Math.max(controls.getPreferredSize().width, 2 * CARD_INSET
                + plain.stringWidth(WIDEST_LABEL) + VALUE_GAP + bold.stringWidth(WIDEST_ISK));
        JPanel sidebar = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Insets insets = getInsets();
                return new Dimension(contentWidth + insets.left + insets.right, super.getPreferredSize().height);
            }
        };
        sidebar.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 12));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 10, 0);
        sidebar.add(controls, c);
        sidebar.add(summaryPanel, c);
        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        saveRow.add(saveButton);
        sidebar.add(saveRow, c);
        c.weighty = 1;
        sidebar.add(Box.createGlue(), c);
        add(sidebar, BorderLayout.WEST);

        installTable(daysTable, daysModel, "missionDays");
        installTable(agentsTable, agentsModel, "missionAgents");
        EveEntityCells.install(agentsTable, appContext.itemIconService);
        EveEntityCells.installStandingsLinks(agentsTable, this::showInStandings);
        JPanel agents = titled(new JLabel("By agent"), agentsTable);
        agents.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, titled(daysTitle, daysTable), agents);
        split.setResizeWeight(DEFAULT_DIVIDER);
        split.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
        DividerMemory.install(split, appContext.settingsDao, SettingsDao.MISSION_REPORT_DIVIDER, DEFAULT_DIVIDER);
        add(split, BorderLayout.CENTER);
    }

    private JPanel controls() {
        JPanel controls = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 6, 8);
        controls.add(new JLabel("Report:"), c);
        JPanel periodRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        periodRow.add(periodCombo);
        periodRow.add(Box.createHorizontalStrut(6));
        periodRow.add(todayButton);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        controls.add(periodRow, c);
        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 6, 8);
        controls.add(new JLabel("Character:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        controls.add(characterCombo, c);
        JPanel navigation = new JPanel(new BorderLayout(4, 0));
        navigation.add(previousButton, BorderLayout.WEST);
        navigation.add(periodLabel, BorderLayout.CENTER);
        navigation.add(nextButton, BorderLayout.EAST);
        FontMetrics bold = periodLabel.getFontMetrics(periodLabel.getFont());
        navigation.setPreferredSize(new Dimension(bold.stringWidth(WIDEST_PERIOD) + 16
                + 2 * previousButton.getPreferredSize().width, navigation.getPreferredSize().height));
        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 0, 0, 0);
        controls.add(navigation, c);
        return controls;
    }

    private <T> void installTable(JTable table, ColumnTableModel<T> model, String key) {
        SortableTables.withColumnChooser(table, model, key, appContext.tableColumnStateDao);
        TableStyler.sizeColumnsFromSamples(table, MissionReportPanel::columnSample);
    }

    private static String columnSample(Object key) {
        return switch (String.valueOf(key)) {
            case "day" -> "Week of 0000-00-00";
            case "agent", "corporation" -> "Mmmmmmmm Mmmmmmmmmmmm";
            case "faction" -> "Mmmmmmmm Mmmmmmmmm";
            case "missions" -> "0000";
            case "lp" -> "0,000,000";
            default -> WIDEST_ISK;
        };
    }

    private static JPanel titled(JLabel title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(title, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void apply(Loaded result) {
        loaded = result;
        updatingFilters = true;
        Object previous = characterCombo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(ALL_CHARACTERS);
        Stream.concat(result.payments().stream().map(Payment::characterName),
                        result.gains().stream().map(LpGain::characterName))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .forEach(model::addElement);
        characterCombo.setModel(model);
        characterCombo.setSelectedItem(previous != null && model.getIndexOf(previous) >= 0 ? previous
                : ALL_CHARACTERS);
        updatingFilters = false;
        showReport();
    }

    private void periodChanged() {
        Period chosen = (Period) periodCombo.getSelectedItem();
        if (chosen == null || chosen == period) {
            return;
        }
        LocalDate today = LocalDate.now(zone);
        LocalDate anchor = !today.isBefore(from) && !today.isAfter(end(today)) ? today : from;
        period = chosen;
        from = period.start(anchor, firstDayOfWeek);
        appContext.settingsDao.set(SettingsDao.MISSION_REPORT_PERIOD, period.name());
        showReport();
    }

    private void move(int periods) {
        from = period.shift(from, periods);
        showReport();
    }

    private void showReport() {
        String character = selectedCharacter();
        List<Payment> payments = loaded.payments().stream()
                .filter(payment -> character == null || character.equals(payment.characterName()))
                .toList();
        List<LpGain> gains = loaded.gains().stream()
                .filter(gain -> character == null || character.equals(gain.characterName()))
                .toList();
        LocalDate today = LocalDate.now(zone);
        Optional<LocalDate> firstData = Stream.concat(payments.stream().map(Payment::paidAt),
                        gains.stream().map(LpGain::seenAt))
                .min(Comparator.naturalOrder())
                .map(instant -> instant.atZone(zone).toLocalDate());
        boolean calendar = period.followsCalendar();
        if (!calendar) {
            from = firstData.filter(first -> first.isBefore(today)).orElse(today);
        }
        LocalDate to = end(today);
        report = MissionIncome.report(payments, gains, from, to, zone);
        notes = notes(report, lpFirstRecorded(character, gains));

        String noun = period.toString().toLowerCase(Locale.ROOT);
        periodLabel.setText(MissionReport.periodName(period, from, to));
        previousButton.setEnabled(calendar && firstData.map(first -> first.isBefore(from)).orElse(false));
        previousButton.setToolTipText(calendar ? "The " + noun + " before" : null);
        nextButton.setEnabled(calendar && to.isBefore(today));
        nextButton.setToolTipText(calendar ? "The " + noun + " after" : null);
        todayButton.setEnabled(calendar && (today.isBefore(from) || today.isAfter(to)));
        todayButton.setToolTipText(calendar ? "Go back to the " + noun + " of today" : null);

        showSummary(report, loaded.targetIskPerLp());
        Grouping grouping = period.grouping();
        daysTitle.setText(MissionDaysTableModel.title(grouping));
        daysModel.setRows(MissionIncome.group(report.days(), grouping, firstDayOfWeek).stream()
                .map(day -> new MissionDaysTableModel.Row(day, grouping))
                .toList());
        List<Row> agentRows = report.agents().stream()
                .map(agent -> new Row(agent, agent.agentName() == null ? null
                        : loaded.agents().get(agent.agentName())))
                .toList();
        agentsModel.setRows(agentRows);
        EveEntityCells.loadImages(agentRows.stream().flatMap(Row::links), appContext.itemIconService,
                agentsTable::repaint);
    }

    private void showInStandings(EveEntity entity) {
        MissionAgent agent = loaded.agents().values().stream()
                .filter(candidate -> entity.kind() == Kind.AGENT ? entity.id() == candidate.agentId()
                        : entity.id().equals(candidate.corporationId()))
                .findFirst()
                .orElse(null);
        showStandings.accept(StandingTarget.of(entity, agent, selectedCharacter()));
    }

    private LocalDate end(LocalDate today) {
        return period.followsCalendar() ? period.end(from) : today;
    }

    private String selectedCharacter() {
        Object selected = characterCombo.getSelectedItem();
        return selected == null || ALL_CHARACTERS.equals(selected) ? null : (String) selected;
    }

    private Instant lpFirstRecorded(String character, List<LpGain> gains) {
        if (character == null) {
            return loaded.lpFirstRecorded().values().stream().min(Comparator.naturalOrder()).orElse(null);
        }
        return gains.stream()
                .map(LpGain::characterId)
                .distinct()
                .map(loaded.lpFirstRecorded()::get)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private List<String> notes(Report report, Instant lpFirstRecorded) {
        List<String> result = new ArrayList<>();
        if (lpFirstRecorded == null) {
            result.add("EVE Farm hasn't saved your LP yet. It reads it from EVE once an hour.");
        } else if (lpFirstRecorded.atZone(zone).toLocalDate().isAfter(report.from())) {
            result.add("EVE Farm started saving your LP on " + SINCE.format(lpFirstRecorded.atZone(zone))
                    + ": LP earned before then isn't counted. EVE doesn't keep a history of it.");
        }
        if (report.lpEarnedSince() != null) {
            result.add("The LP includes LP earned since " + SINCE.format(report.lpEarnedSince().atZone(zone))
                    + ", when EVE Farm read it before.");
        }
        return result;
    }

    private void showSummary(Report report, double targetIskPerLp) {
        MissionReport.Overview overview = MissionReport.overview(report, targetIskPerLp);
        summaryPanel.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, CARD_GAP, 0);
        summaryPanel.add(headlineCard(overview), c);
        for (MissionReport.Card card : overview.cards()) {
            summaryPanel.add(card(card), c);
        }
        if (overview.notCounted() != null) {
            JPanel row = new JPanel(new GridBagLayout());
            row.setBorder(BorderFactory.createEmptyBorder(0, CARD_INSET, 0, CARD_INSET));
            addLine(row, 0, muted(overview.notCounted().label(), overview.notCounted().tip()),
                    muted(overview.notCounted().value(), overview.notCounted().tip()), 0, 0);
            summaryPanel.add(row, c);
        }
        for (String note : notes) {
            summaryPanel.add(note(note), c);
        }
        summaryPanel.revalidate();
        summaryPanel.repaint();
    }

    private static JComponent headlineCard(MissionReport.Overview overview) {
        CardPanel card = new CardPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(CARD_PADDING, 0, 0, 0);
        card.add(muted(overview.headline().label(), overview.headline().tip()), c);
        JLabel value = new JLabel(overview.headline().value());
        value.setFont(value.getFont().deriveFont(Font.BOLD, value.getFont().getSize2D() + HEADLINE_GROWTH));
        value.setToolTipText(overview.headline().tip());
        c.insets = new Insets(0, 0, 0, 0);
        card.add(value, c);
        c.insets = new Insets(0, 0, CARD_PADDING, 0);
        card.add(muted(overview.caption(), overview.captionTip()), c);
        return card;
    }

    private static JComponent card(MissionReport.Card section) {
        CardPanel card = new CardPanel(new GridBagLayout());
        JLabel total = new JLabel(section.total().value());
        total.setFont(total.getFont().deriveFont(Font.BOLD, total.getFont().getSize2D() + 1f));
        total.setToolTipText(section.total().tip());
        addLine(card, 0, muted(section.total().label(), section.total().tip()), total, CARD_PADDING,
                section.rows().isEmpty() ? CARD_PADDING : 0);
        if (section.rows().isEmpty()) {
            return card;
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 3, 0);
        card.add(new JSeparator(), c);
        int row = 2;
        for (MissionReport.Line line : section.rows()) {
            boolean last = row == section.rows().size() + 1;
            JLabel label = new JLabel(line.label());
            label.setToolTipText(line.tip());
            label.putClientProperty("html.disable", Boolean.TRUE);
            JLabel value = new JLabel(line.value());
            value.setToolTipText(line.tip());
            addLine(card, row++, label, value, 3, last ? CARD_PADDING : 0);
        }
        return card;
    }

    private static void addLine(JPanel panel, int row, JLabel label, JLabel value, int top, int bottom) {
        label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(top, 0, bottom, VALUE_GAP);
        panel.add(label, c);
        c.gridx = 1;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(top, 0, bottom, 0);
        panel.add(value, c);
    }

    private static JLabel muted(String text, String tip) {
        JLabel label = UiColors.mutedLabel(text);
        label.putClientProperty("html.disable", Boolean.TRUE);
        label.setToolTipText(tip);
        return label;
    }

    private JComponent note(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UIManager.getFont("Label.font"));
        area.setForeground(UiColors.muted());
        area.setBorder(BorderFactory.createEmptyBorder(0, CARD_INSET, 0, CARD_INSET));
        area.setSize(new Dimension(contentWidth, Short.MAX_VALUE));
        return area;
    }

    private void saveReport() {
        if (report == null) {
            return;
        }
        Report saved = report;
        Period savedPeriod = period;
        List<String> savedNotes = notes;
        String characters = Optional.ofNullable(selectedCharacter()).orElse(ALL_CHARACTERS);
        double targetIskPerLp = loaded.targetIskPerLp();
        String fileName = "mission-report-" + savedPeriod.toString().toLowerCase(Locale.ROOT).replace(' ', '-') + "-"
                + saved.from() + ".txt";
        TextReportSaver.save(this, SAVE_TITLE, fileName,
                () -> MissionReport.lines(saved, savedPeriod, characters, targetIskPerLp, savedNotes, Instant.now()),
                busy -> saveButton.setEnabled(!busy));
    }
}
