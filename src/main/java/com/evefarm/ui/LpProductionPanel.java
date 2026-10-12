package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.db.dao.SettingsDao;
import com.evefarm.model.LoyaltyPointCheck;
import com.evefarm.model.LpGain;
import com.evefarm.service.LpProduction;
import com.evefarm.service.LpProduction.Day;
import com.evefarm.ui.column.ColumnSorting;
import com.evefarm.ui.column.ColumnVisibilitySupport;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LpProductionPanel extends JPanel {

    private static final String ALL_CHARACTERS = "All characters";
    private static final String ALL_CORPORATIONS = "All corporations";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter SHORT_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter SINCE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US);
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.US);
    private static final int TIP_LINES = 6;

    private record Loaded(List<LpGain> gains, Map<Long, Instant> firstRecorded, Map<Long, LoyaltyPointCheck> checks,
                          double targetIskPerLp) {
    }

    private final AppContext appContext;
    private final ZoneId zone = ZoneId.systemDefault();
    private final JComboBox<String> characterCombo = new JComboBox<>();
    private final JComboBox<String> corporationCombo = new JComboBox<>();
    private final LpHeatmap heatmap = new LpHeatmap(WeekFields.of(Locale.getDefault()).getFirstDayOfWeek());
    private final JLabel summaryLabel = new JLabel(" ");
    private final JLabel syncLabel = UiColors.mutedLabel(" ");
    private final JLabel dayLabel = new JLabel(" ");
    private final LpGainsTableModel gainsModel = new LpGainsTableModel();
    private final JTable gainsTable = new JTable(gainsModel);
    private final AtomicInteger generation = new AtomicInteger();
    private Loaded loaded = new Loaded(List.of(), Map.of(), Map.of(), 0);
    private Map<LocalDate, Day> days = Map.of();
    private LocalDate trackedFrom;
    private LocalDate selected;
    private boolean updatingFilters;

    public LpProductionPanel(AppContext appContext) {
        super(new BorderLayout());
        this.appContext = appContext;
        buildUi();
        appContext.loyaltyPointService.addChangeListener(() -> SwingUtilities.invokeLater(() -> {
            if (isShowing()) {
                onShown();
            }
        }));
    }

    public void onShown() {
        BackgroundLoader.load(generation, () -> new Loaded(appContext.loyaltyPointService.gains(),
                        appContext.loyaltyPointService.firstRecorded(), appContext.loyaltyPointService.checks(),
                        appContext.settingsDao.getDouble(SettingsDao.LP_STORE_TARGET_ISK_PER_LP, 500)),
                this::apply, "the LP earned per day",
                () -> summaryLabel.setText("Couldn't load the LP history - see the log."));
    }

    private void buildUi() {
        characterCombo.addActionListener(e -> filtersChanged());
        corporationCombo.addActionListener(e -> filtersChanged());
        ComboSizing.names(characterCombo);
        ComboSizing.names(corporationCombo);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        filters.add(new JLabel("Character:"));
        filters.add(characterCombo);
        filters.add(new JLabel("Corporation:"));
        filters.add(corporationCombo);
        add(filters, BorderLayout.NORTH);

        heatmap.setTips(this::tipFor);
        heatmap.setOnSelect(this::selectDay);
        JPanel heatmapRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        heatmapRow.add(heatmap);
        JScrollPane heatmapScroll = new JScrollPane(heatmapRow, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        heatmapScroll.setBorder(BorderFactory.createEmptyBorder());
        summaryLabel.setToolTipText("LP is counted each time EVE Farm reads it from EVE: once an hour, as soon as "
                + "EVE renews it. LP spent between two reads isn't seen");
        JPanel overview = new JPanel(new BorderLayout(0, 8));
        overview.setBorder(BorderFactory.createEmptyBorder(4, 12, 10, 12));
        overview.add(heatmapScroll, BorderLayout.NORTH);
        JPanel summaryLines = new JPanel(new BorderLayout(0, 4));
        summaryLines.add(summaryLabel, BorderLayout.NORTH);
        summaryLines.add(syncLabel, BorderLayout.SOUTH);
        syncLabel.setToolTipText("EVE renews your LP once an hour. EVE Farm reads it as soon as EVE has new data, "
                + "and counts it at the time EVE's data is from");
        overview.add(summaryLines, BorderLayout.CENTER);

        gainsTable.setRowSorter(sortedBy(gainsModel));
        TableStyler.style(gainsTable);
        ColumnVisibilitySupport.install(gainsTable, gainsModel, "lpGains", appContext.tableColumnStateDao);
        JPanel dayPanel = new JPanel(new BorderLayout(0, 4));
        dayPanel.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
        dayPanel.add(dayLabel, BorderLayout.NORTH);
        dayPanel.add(new JScrollPane(gainsTable), BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout());
        center.add(overview, BorderLayout.NORTH);
        center.add(dayPanel, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        selectDay(null);
    }

    private static TableRowSorter<LpGainsTableModel> sortedBy(LpGainsTableModel model) {
        TableRowSorter<LpGainsTableModel> sorter = new TableRowSorter<>(model);
        ColumnSorting.install(sorter, model);
        return sorter;
    }

    private void apply(Loaded result) {
        loaded = result;
        updatingFilters = true;
        refill(characterCombo, ALL_CHARACTERS, result.gains().stream().map(LpGain::characterName));
        refill(corporationCombo, ALL_CORPORATIONS, result.gains().stream().map(LpGain::corporationName));
        updatingFilters = false;
        showFiltered();
    }

    private static void refill(JComboBox<String> combo, String all, Stream<String> names) {
        Object previous = combo.getSelectedItem();
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(all);
        names.distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(model::addElement);
        combo.setModel(model);
        combo.setSelectedItem(previous != null && model.getIndexOf(previous) >= 0 ? previous : all);
    }

    private void filtersChanged() {
        if (!updatingFilters) {
            showFiltered();
        }
    }

    private void showFiltered() {
        String character = (String) characterCombo.getSelectedItem();
        String corporation = (String) corporationCombo.getSelectedItem();
        List<LpGain> shown = loaded.gains().stream()
                .filter(gain -> isAll(character, ALL_CHARACTERS) || character.equals(gain.characterName()))
                .filter(gain -> isAll(corporation, ALL_CORPORATIONS) || corporation.equals(gain.corporationName()))
                .toList();
        days = LpProduction.byDay(shown, zone);
        LocalDate today = LocalDate.now(zone);
        trackedFrom = trackedFrom(character);
        List<LoyaltyPointCheck> checks = characterIds(character).stream()
                .map(loaded.checks()::get)
                .filter(Objects::nonNull)
                .toList();
        syncLabel.setText(syncText(checks.stream().map(LoyaltyPointCheck::dataFrom).min(Comparator.naturalOrder())
                        .orElse(null),
                checks.stream().map(LoyaltyPointCheck::nextRead).filter(Objects::nonNull)
                        .min(Comparator.naturalOrder()).orElse(null), Instant.now(), zone));
        heatmap.setData(days, trackedFrom, today);
        summaryLabel.setText(summary(days, today, loaded.targetIskPerLp()));
        if (selected == null || selected.isAfter(today) || selected.isBefore(heatmap.firstShownDay())) {
            selected = days.keySet().stream().max(Comparator.naturalOrder()).orElse(null);
        }
        selectDay(selected);
    }

    private static boolean isAll(String selection, String all) {
        return selection == null || all.equals(selection);
    }

    private Set<Long> characterIds(String character) {
        if (isAll(character, ALL_CHARACTERS)) {
            Set<Long> all = new HashSet<>(loaded.firstRecorded().keySet());
            all.addAll(loaded.checks().keySet());
            return all;
        }
        return loaded.gains().stream()
                .filter(gain -> character.equals(gain.characterName()))
                .map(LpGain::characterId)
                .collect(Collectors.toSet());
    }

    private LocalDate trackedFrom(String character) {
        return characterIds(character).stream()
                .map(loaded.firstRecorded()::get)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(instant -> instant.atZone(zone).toLocalDate())
                .orElse(null);
    }

    static String summary(Map<LocalDate, Day> days, LocalDate today, double targetIskPerLp) {
        if (days.isEmpty()) {
            return "No LP earned that EVE Farm has seen yet. It reads your LP from EVE once an hour, as soon as EVE "
                    + "renews it.";
        }
        long week = LpProduction.total(days, today.minusDays(6), today);
        long month = LpProduction.total(days, today.minusDays(29), today);
        StringBuilder text = new StringBuilder(String.format(Locale.US, "Last 7 days: %,d LP  ·  Last 30 days: %,d LP",
                week, month));
        if (targetIskPerLp > 0 && month > 0) {
            text.append(", about ").append(isk(month * targetIskPerLp)).append(" at ")
                    .append(String.format(Locale.US, "%,.0f", targetIskPerLp)).append(" ISK/LP");
        }
        LpProduction.best(days, today.minusDays(364), today).ifPresent(best -> text.append(String.format(Locale.US,
                "  ·  Best day: %s, %,d LP", SHORT_DAY.format(best.date()), best.points())));
        return text.toString();
    }

    static String syncText(Instant dataFrom, Instant nextRead, Instant now, ZoneId zone) {
        if (dataFrom == null) {
            return "EVE Farm hasn't read your LP from EVE yet.";
        }
        StringBuilder text = new StringBuilder("Matches EVE as of ").append(clock(dataFrom, now, zone));
        if (nextRead != null) {
            text.append(nextRead.isAfter(now) ? "  ·  next read at " + clock(nextRead, now, zone)
                    : "  ·  next read in a few minutes");
        }
        return text.append("  ·  EVE renews LP once an hour").toString();
    }

    private static String clock(Instant instant, Instant now, ZoneId zone) {
        return instant.atZone(zone).toLocalDate().equals(now.atZone(zone).toLocalDate())
                ? CLOCK.format(instant.atZone(zone)) : SINCE.format(instant.atZone(zone));
    }

    private static String isk(double value) {
        return new CompactIskNumberFormat().format(value) + " ISK";
    }

    private String tipFor(LocalDate date) {
        String day = DAY.format(date);
        if (trackedFrom == null || date.isBefore(trackedFrom)) {
            return day + ": before EVE Farm started saving your LP";
        }
        Day lp = days.get(date);
        if (lp == null) {
            return day + ": no LP earned";
        }
        StringBuilder text = new StringBuilder(String.format(Locale.US, "%s: %,d LP", day, lp.points()));
        boolean everyCharacter = isAll((String) characterCombo.getSelectedItem(), ALL_CHARACTERS);
        Map<String, Long> bySource = new LinkedHashMap<>();
        for (LpGain gain : lp.gains()) {
            String source = everyCharacter ? gain.characterName() + " - " + gain.corporationName()
                    : gain.corporationName();
            bySource.merge(source, gain.points(), Long::sum);
        }
        List<Map.Entry<String, Long>> sources = bySource.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .toList();
        sources.stream().limit(TIP_LINES).forEach(source -> text.append(String.format(Locale.US, "\n%s: %,d",
                source.getKey(), source.getValue())));
        if (sources.size() > TIP_LINES) {
            text.append("\nand ").append(sources.size() - TIP_LINES).append(" more");
        }
        if (loaded.targetIskPerLp() > 0) {
            text.append("\nAbout ").append(isk(lp.points() * loaded.targetIskPerLp())).append(" at your target of ")
                    .append(String.format(Locale.US, "%,.0f", loaded.targetIskPerLp())).append(" ISK/LP");
        }
        if (lp.includesEarlierDays()) {
            text.append("\nIncludes LP earned since ").append(SINCE.format(lp.earnedSince().atZone(zone)))
                    .append(", when EVE Farm read it before");
        }
        return text.toString();
    }

    private void selectDay(LocalDate date) {
        selected = date;
        heatmap.select(date);
        Day day = date == null ? null : days.get(date);
        gainsModel.setRows(day == null ? List.of() : day.gains());
        TableStyler.packColumns(gainsTable);
        if (date == null) {
            dayLabel.setText("Click a day to see where its LP came from.");
        } else if (day == null) {
            dayLabel.setText(DAY.format(date) + ": no LP earned");
        } else {
            dayLabel.setText(String.format(Locale.US, "%s: %,d LP", DAY.format(date), day.points()));
        }
    }
}
