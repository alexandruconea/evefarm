package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.LpGain;
import com.evefarm.service.MissionIncome;
import com.evefarm.service.MissionIncome.Payment;
import com.evefarm.service.MissionIncome.Period;
import com.evefarm.service.MissionIncome.Report;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnTableModel;
import com.evefarm.ui.column.ColumnVisibilitySupport;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

public final class MissionReportPanel extends JPanel {

    private static final Logger LOG = Logger.getLogger(MissionReportPanel.class.getName());
    private static final String ALL_CHARACTERS = "All characters";
    private static final String SAVE_TITLE = "Save Mission Report";
    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US);
    private static final double DEFAULT_DIVIDER = 0.6;
    private static final String WIDEST_PERIOD = "Wed, Dec 30, 2026 to Tue, Jan 5, 2027";
    private static final String WIDEST_LABEL = "Other bounties near a turn-in";
    private static final String WIDEST_ISK = "00,000,000,000.00 ISK";
    private static final int DETAIL_INDENT = 16;
    private static final int VALUE_GAP = 24;
    private static final int SUMMARY_INSET = 6;

    private record Loaded(List<Payment> payments, List<LpGain> gains, Map<Long, Instant> lpFirstRecorded,
                          double targetIskPerLp) {
    }

    private final AppContext appContext;
    private final ZoneId zone = ZoneId.systemDefault();
    private final DayOfWeek firstDayOfWeek = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
    private final JComboBox<Period> periodCombo = new JComboBox<>(Period.values());
    private final JButton previousButton = new JButton("<");
    private final JButton nextButton = new JButton(">");
    private final JButton todayButton = new JButton("Today");
    private final JLabel periodLabel = new JLabel(" ");
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JButton saveButton = new JButton("Save Report...", Icons.SAVE);
    private final JPanel summaryGrid = new JPanel(new GridBagLayout());
    private final MissionDaysTableModel daysModel = new MissionDaysTableModel();
    private final JTable daysTable = new JTable(daysModel);
    private final MissionAgentsTableModel agentsModel = new MissionAgentsTableModel();
    private final JTable agentsTable = new JTable(agentsModel);
    private final AtomicInteger generation = new AtomicInteger();
    private Loaded loaded = new Loaded(List.of(), List.of(), Map.of(), 0);
    private Period period;
    private LocalDate from;
    private Report report;
    private List<String> notes = List.of();
    private boolean updatingFilters;
    private int contentWidth;

    public MissionReportPanel(AppContext appContext) {
        super(new BorderLayout());
        this.appContext = appContext;
        period = appContext.settingsDao.getEnum(SettingsDao.MISSION_REPORT_PERIOD, Period.class).orElse(Period.WEEK);
        from = period.start(LocalDate.now(zone), firstDayOfWeek);
        buildUi();
        appContext.loyaltyPointService.addChangeListener(() -> SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                onShown();
            }
        }));
    }

    public void onShown() {
        BackgroundLoader.load(generation, () -> new Loaded(
                        MissionIncome.classify(appContext.walletJournalDao.findMissionIncome(),
                                appContext.npcCatalogService.catalog()::isMissionNpc),
                        appContext.loyaltyPointService.gains(), appContext.loyaltyPointService.firstRecorded(),
                        appContext.settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP,
                                SettingsDao.DEFAULT_LP_STORE_TARGET_ISK_PER_LP)),
                this::apply, "the mission report", () -> {
                    summaryGrid.removeAll();
                    summaryGrid.add(new JLabel("Couldn't load the mission report - see the log."));
                    summaryGrid.revalidate();
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
        saveButton.addActionListener(e -> saveReport());
        saveButton.setToolTipText("Save this report as a text file");

        FontMetrics plain = periodLabel.getFontMetrics(periodLabel.getFont());
        FontMetrics bold = periodLabel.getFontMetrics(periodLabel.getFont().deriveFont(Font.BOLD));
        periodLabel.setPreferredSize(new Dimension(plain.stringWidth(WIDEST_PERIOD) + 8,
                periodLabel.getPreferredSize().height));
        List<JPanel> rows = List.of(row(new JLabel("Report:"), periodCombo, todayButton),
                row(previousButton, periodLabel, nextButton), row(new JLabel("Character:"), characterCombo),
                row(saveButton));
        contentWidth = SUMMARY_INSET + DETAIL_INDENT + bold.stringWidth(WIDEST_LABEL) + VALUE_GAP
                + bold.stringWidth(WIDEST_ISK);
        for (JPanel row : rows) {
            contentWidth = Math.max(contentWidth, row.getPreferredSize().width);
        }
        JPanel sidebar = new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                Insets insets = getInsets();
                return new Dimension(contentWidth + insets.left + insets.right, super.getPreferredSize().height);
            }
        };
        sidebar.setBorder(BorderFactory.createEmptyBorder(6, 6, 8, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        for (JPanel row : rows) {
            sidebar.add(row, c);
        }
        c.insets = new Insets(8, SUMMARY_INSET, 0, 0);
        sidebar.add(summaryGrid, c);
        c.weighty = 1;
        sidebar.add(Box.createGlue(), c);
        add(sidebar, BorderLayout.WEST);

        installTable(daysTable, daysModel, "missionDays");
        installTable(agentsTable, agentsModel, "missionAgents");
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, titled("By day", daysTable),
                titled("By agent", agentsTable));
        split.setResizeWeight(DEFAULT_DIVIDER);
        split.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 8));
        DividerMemory.install(split, appContext.settingsDao, SettingsDao.MISSION_REPORT_DIVIDER, DEFAULT_DIVIDER);
        add(split, BorderLayout.CENTER);
    }

    private static JPanel row(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (JComponent component : components) {
            row.add(component);
        }
        return row;
    }

    private <T> void installTable(JTable table, ColumnTableModel<T> model, String key) {
        TableRowSorter<ColumnTableModel<T>> sorter = new TableRowSorter<>(model);
        ColumnSorting.install(sorter, model);
        table.setRowSorter(sorter);
        TableStyler.style(table);
        ColumnVisibilitySupport.install(table, model, key, appContext.tableColumnStateDao);
        TableStyler.sizeColumnsFromSamples(table, MissionReportPanel::columnSample);
    }

    private static String columnSample(Object key) {
        return switch (String.valueOf(key)) {
            case "day" -> "0000-00-00 Wed";
            case "agent" -> "Mmmmmmmm Mmmmmmmm";
            case "missions" -> "0000";
            case "lp" -> "0,000,000";
            default -> WIDEST_ISK;
        };
    }

    private static JPanel titled(String title, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JLabel(title), BorderLayout.NORTH);
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
        LocalDate anchor = !today.isBefore(from) && !today.isAfter(period.end(from)) ? today : from;
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
        LocalDate to = period.end(from);
        report = MissionIncome.report(payments, gains, from, to, zone);
        notes = notes(report, lpFirstRecorded(character, gains));

        LocalDate today = LocalDate.now(zone);
        Optional<LocalDate> firstData = Stream.concat(payments.stream().map(Payment::paidAt),
                        gains.stream().map(LpGain::seenAt))
                .min(Comparator.naturalOrder())
                .map(instant -> instant.atZone(zone).toLocalDate());
        periodLabel.setText(MissionReport.periodName(period, from, to));
        previousButton.setEnabled(firstData.map(first -> first.isBefore(from)).orElse(false));
        previousButton.setToolTipText("The " + period.toString().toLowerCase(Locale.ROOT) + " before");
        nextButton.setEnabled(to.isBefore(today));
        nextButton.setToolTipText("The " + period.toString().toLowerCase(Locale.ROOT) + " after");
        todayButton.setEnabled(today.isBefore(from) || today.isAfter(to));
        todayButton.setToolTipText("Go back to the " + period.toString().toLowerCase(Locale.ROOT) + " of today");

        showSummary(report, loaded.targetIskPerLp());
        daysModel.setRows(report.days());
        agentsModel.setRows(report.agents());
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
        summaryGrid.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        for (MissionReport.Line line : MissionReport.summary(report, targetIskPerLp)) {
            JLabel label = new JLabel(line.label());
            JLabel value = new JLabel(line.value());
            for (JLabel part : List.of(label, value)) {
                part.setToolTipText(line.tip());
                if (!line.detail()) {
                    part.setFont(part.getFont().deriveFont(Font.BOLD));
                }
            }
            label.setMinimumSize(new Dimension(0, label.getPreferredSize().height));
            c.gridx = 0;
            c.weightx = 1;
            c.insets = new Insets(line.detail() ? 0 : 6, line.detail() ? DETAIL_INDENT : 0, 1, VALUE_GAP);
            c.anchor = GridBagConstraints.WEST;
            summaryGrid.add(label, c);
            c.gridx = 1;
            c.weightx = 0;
            c.insets = new Insets(line.detail() ? 0 : 6, 0, 1, 0);
            c.anchor = GridBagConstraints.EAST;
            summaryGrid.add(value, c);
            c.gridy++;
        }
        summaryGrid.revalidate();
        summaryGrid.repaint();
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
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(SAVE_TITLE);
        chooser.setSelectedFile(new File("mission-report-" + savedPeriod.name().toLowerCase(Locale.ROOT) + "-"
                + saved.from() + ".txt"));
        chooser.setFileFilter(new FileNameExtensionFilter("Text file (*.txt)", "txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File chosen = chooser.getSelectedFile();
        Path file = chosen.getName().toLowerCase(Locale.ROOT).endsWith(".txt") ? chosen.toPath()
                : chosen.toPath().resolveSibling(chosen.getName() + ".txt");
        if (Files.exists(file) && JOptionPane.showConfirmDialog(this, file.getFileName()
                + " already exists. Replace it?", SAVE_TITLE, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        saveButton.setEnabled(false);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.write(file, MissionReport.lines(saved, savedPeriod, characters, targetIskPerLp, savedNotes,
                        Instant.now()), StandardCharsets.UTF_8);
                return null;
            }

            @Override
            protected void done() {
                saveButton.setEnabled(true);
                try {
                    get();
                    JOptionPane.showMessageDialog(MissionReportPanel.this, "Report saved to:\n" + file,
                            SAVE_TITLE, JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to save the mission report", e);
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    JOptionPane.showMessageDialog(MissionReportPanel.this, "Couldn't save the report: "
                            + cause.getMessage(), SAVE_TITLE, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
