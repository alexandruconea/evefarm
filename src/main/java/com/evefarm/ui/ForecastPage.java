package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.service.Forecast.Projection;
import com.evefarm.service.ForecastSettings;
import com.evefarm.service.ForecastSettings.Group;
import com.evefarm.service.ForecastSettings.Horizon;
import com.evefarm.service.ForecastSettings.Selection;
import com.evefarm.service.ForecastSettings.View;
import com.evefarm.service.IncomeSources.Source;
import com.evefarm.service.TrackerCombination;
import com.evefarm.service.TrackerCombinationService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class ForecastPage extends TabPanel {

    private static final String TITLE = "Forecast";
    private static final int CHARACTER_WIDTH = 280;

    private record Choice(String key, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private record Loaded(List<EveCharacter> characters, List<Group> groups, List<View> views, View view,
                          List<TrackerCombination> combinations, TrackerCombination combination,
                          Selection selection, Projection projection, Map<Source, Double> earnedPerDay) {
    }

    private final AppContext appContext;
    private final TrackerCombinationService combinations;
    private final ForecastSettings settings;
    private final ForecastPanel forecastPanel = new ForecastPanel();
    private final JComboBox<String> viewCombo = new JComboBox<>();
    private final JButton newViewButton = new JButton("New...", Icons.ADD);
    private final JButton renameViewButton = new JButton("Rename...");
    private final JButton deleteViewButton = new JButton("Delete", Icons.REMOVE);
    private final JComboBox<Choice> characterCombo = new JComboBox<>();
    private final JButton groupsButton = new JButton("Groups...");
    private final JComboBox<String> combinationCombo = new JComboBox<>();
    private final JButton combinationsButton = new JButton("Edit...");
    private final JComboBox<Horizon> horizonCombo = new JComboBox<>(Horizon.values());
    private final JTextField dateField = new JTextField(9);
    private final JComboBox<Choice> windowCombo = new JComboBox<>();
    private final JTextField goalField = new JTextField(9);
    private final JLabel titleLabel = new JLabel(TITLE);
    private final AtomicInteger generation = new AtomicInteger();
    private Loaded loaded;
    private boolean filling;

    public ForecastPage(AppContext appContext) {
        super(new BorderLayout(0, 8));
        this.appContext = appContext;
        this.combinations = appContext.trackerCombinationService;
        this.settings = appContext.forecastSettings;
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        buildUi();
    }

    private void buildUi() {
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD,
                titleLabel.getFont().getSize2D() + 3f));
        for (int days : ForecastSettings.WINDOWS) {
            windowCombo.addItem(new Choice(String.valueOf(days), "Last " + days + " days"));
        }
        ComboSizing.names(viewCombo);
        ComboFilter.install(viewCombo);
        ComboSizing.fix(characterCombo, CHARACTER_WIDTH);
        ComboFilter.install(characterCombo);
        ComboSizing.names(combinationCombo);
        ComboFilter.install(combinationCombo);
        ComboSizing.options(horizonCombo);
        ComboSizing.options(windowCombo);
        Inputs.hint(dateField, "2027-03-31");
        Inputs.check(dateField, text -> parseDate(text) != null);
        Inputs.unit(goalField, "ISK");
        Inputs.hint(goalField, "e.g. 100b");
        Inputs.check(goalField, text -> IskInput.parse(text) != null);
        ComboSizing.input(dateField);
        ComboSizing.input(goalField);

        viewCombo.setToolTipText("A view keeps the characters, what is added up, how far ahead, the days the trend "
                + "comes from and the goal. Every change is kept in the view you are on");
        newViewButton.setToolTipText("Start a new view from this one");
        characterCombo.setToolTipText("Whose ISK to forecast: your favorite character, all characters, a group or "
                + "one character");
        groupsButton.setToolTipText("Make groups of characters, such as your mission alts, to forecast together");
        combinationCombo.setToolTipText("What the forecast adds up. Edit... chooses what each combination adds up");
        horizonCombo.setToolTipText("How far ahead to forecast");
        dateField.setToolTipText("The last day of the forecast, as year-month-day");
        windowCombo.setToolTipText("The days the trend and the band are worked out from. Fewer days follow a new "
                + "pace sooner; more days are steadier");
        goalField.setToolTipText("An amount to reach, such as 100b, 2.5t or 750,000,000. Leave it empty for no goal");

        viewCombo.addActionListener(e -> {
            if (!filling && viewCombo.getSelectedItem() != null) {
                settings.select(viewCombo.getSelectedItem().toString());
                onShown();
            }
        });
        newViewButton.addActionListener(e -> newView());
        renameViewButton.addActionListener(e -> renameView());
        deleteViewButton.addActionListener(e -> deleteView());
        characterCombo.addActionListener(e -> {
            if (!filling && characterCombo.getSelectedItem() instanceof Choice choice) {
                change(view().withCharacters(choice.key()));
            }
        });
        groupsButton.addActionListener(e -> editGroups());
        combinationCombo.addActionListener(e -> {
            if (!filling && combinationCombo.getSelectedItem() != null) {
                change(view().withCombination(combinationCombo.getSelectedItem().toString()));
            }
        });
        combinationsButton.addActionListener(e -> editCombinations());
        horizonCombo.addActionListener(e -> {
            if (!filling && horizonCombo.getSelectedItem() instanceof Horizon horizon) {
                String date = horizon == Horizon.DATE ? defaultDate() : view().date();
                change(view().withHorizon(horizon, date));
            }
        });
        dateField.addActionListener(e -> commitDate());
        dateField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitDate();
            }
        });
        windowCombo.addActionListener(e -> {
            if (!filling && windowCombo.getSelectedItem() instanceof Choice choice) {
                change(view().withWindow(Integer.parseInt(choice.key())));
            }
        });
        goalField.addActionListener(e -> commitGoal());
        goalField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitGoal();
            }
        });

        JPanel views = row(titleLabel, Box.createHorizontalStrut(16), new JLabel("View:"), viewCombo,
                newViewButton, renameViewButton, deleteViewButton);
        JPanel options = row(new JLabel("Characters:"), characterCombo, groupsButton, gap(),
                new JLabel("Adds up:"), combinationCombo, combinationsButton, gap(),
                new JLabel("Until:"), horizonCombo, dateField, gap(),
                new JLabel("Trend from:"), windowCombo, gap(),
                new JLabel("Goal:"), goalField);
        JPanel controls = new JPanel(new BorderLayout(0, 6));
        controls.add(views, BorderLayout.NORTH);
        controls.add(options, BorderLayout.CENTER);
        add(controls, BorderLayout.NORTH);
        add(forecastPanel, BorderLayout.CENTER);
    }

    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (Component component : components) {
            row.add(component);
        }
        return row;
    }

    private static Component gap() {
        return Box.createHorizontalStrut(10);
    }

    @Override
    void onShown() {
        BackgroundLoader.load(generation, this::load, this::show, "the forecast");
    }

    private Loaded load() {
        List<EveCharacter> characters = appContext.characterService.listCharactersMainFirst();
        List<Group> groups = settings.groups();
        List<View> views = settings.views();
        View view = settings.currentView();
        List<TrackerCombination> all = combinations.combinations();
        TrackerCombination combination = view.combination() == null ? combinations.forecastCombination()
                : combinations.find(view.combination()).orElseGet(combinations::forecastCombination);
        Selection selection = ForecastSettings.resolve(view.characters(), characters, groups);
        if (selection.characterIds().isEmpty()) {
            return new Loaded(characters, groups, views, view, all, combination, selection, null, Map.of());
        }
        Instant now = combinations.clock().instant();
        Instant end = view.horizon().end(now, combinations.clock().getZone(), view.date());
        return new Loaded(characters, groups, views, view, all, combination, selection,
                combinations.project(combination, selection.characterIds(), end, view.window()),
                combinations.earnedPerDay(EnumSet.allOf(Source.class), selection.characterIds(), view.window()));
    }

    private void show(Loaded result) {
        loaded = result;
        View view = result.view();
        filling = true;
        try {
            viewCombo.removeAllItems();
            result.views().forEach(saved -> viewCombo.addItem(saved.name()));
            viewCombo.setSelectedItem(view.name());
            deleteViewButton.setEnabled(result.views().size() > 1);

            characterCombo.removeAllItems();
            for (Choice choice : characterChoices(result.characters(), result.groups())) {
                characterCombo.addItem(choice);
                if (choice.key().equals(view.characters())) {
                    characterCombo.setSelectedItem(choice);
                }
            }
            combinationCombo.removeAllItems();
            result.combinations().forEach(combination -> combinationCombo.addItem(combination.name()));
            if (result.combinations().stream().noneMatch(c -> c.name().equals(result.combination().name()))) {
                combinationCombo.addItem(result.combination().name());
            }
            combinationCombo.setSelectedItem(result.combination().name());
            horizonCombo.setSelectedItem(view.horizon());
            dateField.setVisible(view.horizon() == Horizon.DATE);
            dateField.setText(view.date() == null ? "" : view.date());
            for (int i = 0; i < windowCombo.getItemCount(); i++) {
                if (windowCombo.getItemAt(i).key().equals(String.valueOf(view.windowDays()))) {
                    windowCombo.setSelectedIndex(i);
                }
            }
            goalField.setText(view.goal() == null ? "" : IskInput.format(view.goal()));
        } finally {
            filling = false;
        }
        revalidate();
        if (result.characters().isEmpty()) {
            forecastPanel.showProjection("your characters", result.combination(), null, null, view.windowDays());
            forecastPanel.showSources(Map.of(), result.combination().sources(), view.windowDays());
            return;
        }
        forecastPanel.showProjection(result.selection().label(), result.combination(), result.projection(),
                view.goal(), view.windowDays());
        forecastPanel.showSources(result.earnedPerDay(), result.combination().sources(), view.windowDays());
    }

    private static List<Choice> characterChoices(List<EveCharacter> characters, List<Group> groups) {
        List<Choice> choices = new ArrayList<>();
        String favorite = characters.isEmpty() ? "" : " (" + characters.getFirst().characterName() + ")";
        choices.add(new Choice(ForecastSettings.MAIN, "Favorite character" + favorite));
        choices.add(new Choice(ForecastSettings.ALL, "All characters"));
        groups.forEach(group -> choices.add(new Choice(ForecastSettings.GROUP_PREFIX + group.name(),
                "Group: " + group.name())));
        characters.forEach(character -> choices.add(new Choice(ForecastSettings.CHARACTER_PREFIX
                + character.characterId(), character.characterName())));
        return choices;
    }

    private View view() {
        return loaded == null ? settings.currentView() : loaded.view();
    }

    private void change(View changed) {
        if (Objects.equals(changed, view())) {
            return;
        }
        settings.saveCurrent(changed);
        onShown();
    }

    private void commitDate() {
        if (filling || view().horizon() != Horizon.DATE) {
            return;
        }
        LocalDate date = parseDate(dateField.getText());
        if (date != null && !date.toString().equals(view().date())) {
            change(view().withHorizon(Horizon.DATE, date.toString()));
        }
    }

    private void commitGoal() {
        if (filling) {
            return;
        }
        String text = goalField.getText();
        if (text.isBlank()) {
            change(view().withGoal(null));
            return;
        }
        Double goal = IskInput.parse(text);
        if (goal != null && !goal.equals(view().goal())) {
            change(view().withGoal(goal));
        }
    }

    private String defaultDate() {
        return LocalDate.now(combinations.clock().getZone()).plusMonths(3).toString();
    }

    private void newView() {
        String name = askViewName("Name of the new view:", "");
        if (name == null) {
            return;
        }
        List<View> views = new ArrayList<>(settings.views());
        views.add(view().withName(name));
        settings.saveViews(views);
        settings.select(name);
        onShown();
    }

    private void renameView() {
        String old = view().name();
        String name = askViewName("Rename the view to:", old);
        if (name == null || name.equals(old)) {
            return;
        }
        settings.saveViews(settings.views().stream()
                .map(saved -> saved.name().equals(old) ? saved.withName(name) : saved).toList());
        settings.select(name);
        onShown();
    }

    private void deleteView() {
        String name = view().name();
        List<View> views = settings.views();
        if (views.size() < 2) {
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this, "Delete the view " + name + "?", TITLE,
                JOptionPane.OK_CANCEL_OPTION);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        List<View> left = views.stream().filter(saved -> !saved.name().equals(name)).toList();
        settings.saveViews(left);
        settings.select(left.getFirst().name());
        onShown();
    }

    private String askViewName(String question, String current) {
        Object answer = JOptionPane.showInputDialog(this, question, TITLE, JOptionPane.PLAIN_MESSAGE, null, null,
                current);
        String name = answer == null ? "" : answer.toString().strip();
        if (name.isEmpty()) {
            return null;
        }
        if (!name.equals(current) && settings.views().stream().anyMatch(view -> view.name().equals(name))) {
            JOptionPane.showMessageDialog(this, "There is already a view called " + name + ".", TITLE,
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return name;
    }

    private void editGroups() {
        if (loaded == null) {
            return;
        }
        new CharacterGroupsDialog(SwingUtilities.getWindowAncestor(this), loaded.characters(), loaded.groups(),
                groups -> {
                    settings.saveGroups(groups);
                    onShown();
                }).setVisible(true);
    }

    private void editCombinations() {
        new TrackerCombinationsDialog(SwingUtilities.getWindowAncestor(this), combinations,
                combinationCombo.getSelectedItem() == null ? null : combinationCombo.getSelectedItem().toString(),
                name -> change(view().withCombination(name))).setVisible(true);
    }

    static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static final class IskInput {

        private IskInput() {
        }

        static Double parse(String text) {
            String value = text.strip().toLowerCase(Locale.ROOT).replace("isk", "").replace(",", "")
                    .replace(" ", "").replace(" ", "");
            if (value.isEmpty()) {
                return null;
            }
            double scale = switch (value.charAt(value.length() - 1)) {
                case 'k' -> 1e3;
                case 'm' -> 1e6;
                case 'b' -> 1e9;
                case 't' -> 1e12;
                default -> 1;
            };
            if (scale != 1) {
                value = value.substring(0, value.length() - 1);
            }
            try {
                double amount = Double.parseDouble(value) * scale;
                return Double.isFinite(amount) && amount > 0 ? amount : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        static String format(double amount) {
            return new CompactIskNumberFormat(2).format(amount).toLowerCase(Locale.ROOT);
        }
    }
}
