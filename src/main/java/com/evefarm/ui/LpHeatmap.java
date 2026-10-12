package com.evefarm.ui;

import com.evefarm.service.LpProduction;
import com.evefarm.service.LpProduction.Day;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

final class LpHeatmap extends JComponent {

    static final int WEEKS = 53;
    private static final int CELL = 13;
    private static final int GAP = 3;
    private static final int STEP = CELL + GAP;
    private static final int MARKER = 5;
    private static final Color FALLBACK_ACCENT = new Color(0x2D6FD6);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM", Locale.US);
    private static final List<DayOfWeek> LABELED_DAYS = List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY);

    private final DayOfWeek firstDayOfWeek;
    private Map<LocalDate, Day> days = Map.of();
    private LocalDate today = LocalDate.now();
    private LocalDate trackedFrom;
    private LocalDate selected;
    private LpProduction.Scale scale = LpProduction.scale(List.of());
    private Function<LocalDate, String> tips = date -> null;
    private Consumer<LocalDate> onSelect = date -> {
    };

    LpHeatmap(DayOfWeek firstDayOfWeek) {
        this.firstDayOfWeek = firstDayOfWeek;
        setFont(UIManager.getFont("Label.font"));
        setForeground(UIManager.getColor("Label.foreground"));
        setOpaque(false);
        setToolTipText("");
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                LocalDate date = dateAt(e.getPoint());
                if (date != null) {
                    select(date);
                    onSelect.accept(date);
                }
            }
        });
    }

    void setTips(Function<LocalDate, String> tips) {
        this.tips = tips;
    }

    void setOnSelect(Consumer<LocalDate> onSelect) {
        this.onSelect = onSelect;
    }

    void setData(Map<LocalDate, Day> days, LocalDate trackedFrom, LocalDate today) {
        this.days = days;
        this.trackedFrom = trackedFrom;
        this.today = today;
        LocalDate first = firstShownDay();
        scale = LpProduction.scale(days.values().stream()
                .filter(day -> !day.date().isBefore(first))
                .map(Day::points)
                .toList());
        repaint();
    }

    void select(LocalDate date) {
        selected = date;
        repaint();
    }

    LocalDate firstShownDay() {
        return today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek)).minusWeeks(WEEKS - 1);
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        return new Dimension(left(metrics) + WEEKS * STEP - GAP + 2, gridBottom(metrics) + metrics.getHeight() + 10);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        LocalDate date = dateAt(event.getPoint());
        return date == null ? null : tips.apply(date);
    }

    private int left(FontMetrics metrics) {
        int widest = 0;
        for (DayOfWeek day : LABELED_DAYS) {
            widest = Math.max(widest, metrics.stringWidth(label(day)));
        }
        return widest + 8;
    }

    private static int top(FontMetrics metrics) {
        return metrics.getHeight() + 4;
    }

    private static int gridBottom(FontMetrics metrics) {
        return top(metrics) + 7 * STEP - GAP;
    }

    private static String label(DayOfWeek day) {
        return day.getDisplayName(TextStyle.SHORT, Locale.US);
    }

    private Rectangle cellBounds(LocalDate date, FontMetrics metrics) {
        long index = ChronoUnit.DAYS.between(firstShownDay(), date);
        return new Rectangle(left(metrics) + (int) (index / 7) * STEP, top(metrics) + (int) (index % 7) * STEP,
                CELL, CELL);
    }

    LocalDate dateAt(Point point) {
        FontMetrics metrics = getFontMetrics(getFont());
        int x = point.x - left(metrics);
        int y = point.y - top(metrics);
        if (x < 0 || y < 0 || x % STEP >= CELL || y % STEP >= CELL) {
            return null;
        }
        int column = x / STEP;
        int row = y / STEP;
        if (column >= WEEKS || row >= 7) {
            return null;
        }
        LocalDate date = firstShownDay().plusDays(column * 7L + row);
        return date.isAfter(today) ? null : date;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(getFont());
        FontMetrics metrics = g.getFontMetrics();
        Color background = background();
        Color foreground = getForeground() != null ? getForeground() : Color.BLACK;
        Color[] levels = levelColors(accent(), foreground, background);
        Color outline = blend(foreground, background, 0.22);
        paintLabels(g, metrics);
        LocalDate first = firstShownDay();
        for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
            Rectangle cell = cellBounds(date, metrics);
            if (trackedFrom == null || date.isBefore(trackedFrom)) {
                g.setColor(outline);
                g.draw(new RoundRectangle2D.Double(cell.x + 0.5, cell.y + 0.5, cell.width - 1, cell.height - 1, 3, 3));
                continue;
            }
            Day day = days.get(date);
            int level = day == null ? 0 : scale.level(day.points());
            g.setColor(levels[level]);
            g.fill(new RoundRectangle2D.Double(cell.x, cell.y, cell.width, cell.height, 3, 3));
            if (day != null && day.includesEarlierDays()) {
                g.setColor(level >= 3 ? background : foreground);
                g.fill(marker(cell.x, cell.y, cell.width));
            }
            if (date.equals(selected)) {
                g.setColor(foreground);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new RoundRectangle2D.Double(cell.x - 1.5, cell.y - 1.5, cell.width + 3, cell.height + 3, 4, 4));
                g.setStroke(new BasicStroke(1f));
            }
        }
        paintLegend(g, metrics, levels, outline, foreground);
        g.dispose();
    }

    private void paintLabels(Graphics2D g, FontMetrics metrics) {
        g.setColor(UiColors.muted());
        int top = top(metrics);
        for (int row = 0; row < 7; row++) {
            DayOfWeek day = firstDayOfWeek.plus(row);
            if (LABELED_DAYS.contains(day)) {
                g.drawString(label(day), 0, top + row * STEP + (CELL + metrics.getAscent() - metrics.getDescent()) / 2);
            }
        }
        int lastEnd = Integer.MIN_VALUE;
        LocalDate first = firstShownDay();
        for (int column = 0; column < WEEKS; column++) {
            LocalDate weekStart = first.plusWeeks(column);
            LocalDate monthStart = null;
            for (int offset = 0; offset < 7; offset++) {
                if (weekStart.plusDays(offset).getDayOfMonth() == 1) {
                    monthStart = weekStart.plusDays(offset);
                }
            }
            if (monthStart == null && column > 0) {
                continue;
            }
            String text = MONTH.format(monthStart == null ? weekStart : monthStart);
            int x = left(metrics) + column * STEP;
            if (x > lastEnd + 6 && x + metrics.stringWidth(text) <= left(metrics) + WEEKS * STEP) {
                g.drawString(text, x, metrics.getAscent());
                lastEnd = x + metrics.stringWidth(text);
            }
        }
    }

    private void paintLegend(Graphics2D g, FontMetrics metrics, Color[] levels, Color outline, Color foreground) {
        int y = gridBottom(metrics) + 8;
        int baseline = y + (CELL + metrics.getAscent() - metrics.getDescent()) / 2;
        int x = left(metrics);
        g.setColor(outline);
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, CELL - 1, CELL - 1, 3, 3));
        x += CELL + 5;
        g.setColor(UiColors.muted());
        g.drawString("Not saved yet", x, baseline);
        x += metrics.stringWidth("Not saved yet") + 16;
        g.setColor(levels[2]);
        g.fill(new RoundRectangle2D.Double(x, y, CELL, CELL, 3, 3));
        g.setColor(foreground);
        g.fill(marker(x, y, CELL));
        x += CELL + 5;
        g.setColor(UiColors.muted());
        g.drawString("Includes earlier days", x, baseline);

        int right = left(metrics) + WEEKS * STEP - GAP;
        int more = metrics.stringWidth("More");
        g.drawString("More", right - more, baseline);
        int cellX = right - more - 6 - CELL;
        for (int level = levels.length - 1; level >= 0; level--) {
            g.setColor(levels[level]);
            g.fill(new RoundRectangle2D.Double(cellX, y, CELL, CELL, 3, 3));
            cellX -= STEP;
        }
        g.setColor(UiColors.muted());
        g.drawString("Less", cellX + STEP - 6 - metrics.stringWidth("Less"), baseline);
    }

    private static Shape marker(int x, int y, int size) {
        Path2D triangle = new Path2D.Double();
        triangle.moveTo(x + size - MARKER, y);
        triangle.lineTo(x + size, y);
        triangle.lineTo(x + size, y + MARKER);
        triangle.closePath();
        return triangle;
    }

    static Color[] levelColors(Color accent, Color foreground, Color background) {
        return new Color[]{blend(foreground, background, 0.10), blend(accent, background, 0.32),
                blend(accent, background, 0.55), blend(accent, background, 0.78), accent};
    }

    static Color blend(Color color, Color background, double share) {
        return new Color(
                (int) Math.round(color.getRed() * share + background.getRed() * (1 - share)),
                (int) Math.round(color.getGreen() * share + background.getGreen() * (1 - share)),
                (int) Math.round(color.getBlue() * share + background.getBlue() * (1 - share)));
    }

    private Color background() {
        for (Container parent = getParent(); parent != null; parent = parent.getParent()) {
            if (parent.isOpaque() && parent.getBackground() != null) {
                return parent.getBackground();
            }
        }
        Color panel = UIManager.getColor("Panel.background");
        return panel != null ? panel : Color.WHITE;
    }

    private static Color accent() {
        Color color = UIManager.getColor("Component.accentColor");
        return color == null ? FALLBACK_ACCENT : color;
    }
}
