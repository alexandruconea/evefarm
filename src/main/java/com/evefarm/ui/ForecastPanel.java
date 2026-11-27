package com.evefarm.ui;

import com.evefarm.service.Forecast;
import com.evefarm.service.Forecast.Band;
import com.evefarm.service.Forecast.Goal;
import com.evefarm.service.Forecast.Point;
import com.evefarm.service.Forecast.Projection;
import com.evefarm.service.IncomeSources.Source;
import com.evefarm.service.TrackerCombination;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.DeviationRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.data.xy.YIntervalSeries;
import org.jfree.data.xy.YIntervalSeriesCollection;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Stroke;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class ForecastPanel extends JPanel {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final DateTimeFormatter DAY_AND_YEAR = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
    private static final String TOOLTIP_FORMAT = "{1}: {2} ISK";
    private static final Stroke ACTUAL_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke PROJECTED_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f);
    private static final Stroke MARKER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{2f, 3f}, 0f);
    private static final Stroke GOAL_STROKE = new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{8f, 4f}, 0f);
    private static final float BAND_ALPHA = 0.16f;
    private static final double GOAL_IN_VIEW = 1.5;
    private static final int BAR_WIDTH = 160;
    private static final double MIN_SHOWN_PER_DAY = 1_000;
    private static final double MIN_SHOWN_SHARE = 0.005;
    private static final String CHANCE = String.format(Locale.US, "%d in 10 times",
            Math.round((Forecast.HIGH_PERCENTILE - Forecast.LOW_PERCENTILE) * 10));
    private static final String MODEL_RULES = "The pace is what a usual day of the chosen days brought in: a day far "
            + "out of the ordinary, such as one big sale or purchase, counts only as much as a busy day. The band "
            + "comes from replaying the real days in a thousand different orders; " + CHANCE + " the result falls "
            + "inside it.";
    private static final String TRANSFER_RULES = " ISK moved from or to a corporation wallet and donations count "
            + "as moved, not produced.";

    private final ZoneId zone = ZoneId.systemDefault();
    private final JLabel headlineLabel = new JLabel(" ");
    private final JLabel detailLabel = new JLabel(" ");
    private final JLabel goalLabel = new JLabel(" ");
    private final ChartPanel chartPanel = new ChartPanel(null, false);
    private final JPanel sourcesRows = new JPanel(new GridBagLayout());
    private final JLabel sourcesTitle = new JLabel("Where it comes from");

    ForecastPanel() {
        super(new BorderLayout(0, 10));
        setOpaque(false);
        headlineLabel.setFont(headlineLabel.getFont().deriveFont(Font.BOLD, headlineLabel.getFont().getSize2D() + 3f));
        detailLabel.setForeground(UiColors.muted());
        goalLabel.setFont(goalLabel.getFont().deriveFont(Font.BOLD));
        JPanel text = new JPanel(new GridBagLayout());
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 3, 0);
        text.add(headlineLabel, c);
        text.add(detailLabel, c);
        text.add(goalLabel, c);

        chartPanel.setPopupMenu(null);
        chartPanel.setMouseZoomable(false);
        chartPanel.setMinimumDrawWidth(0);
        chartPanel.setMinimumDrawHeight(0);
        chartPanel.setMaximumDrawWidth(Integer.MAX_VALUE);
        chartPanel.setMaximumDrawHeight(Integer.MAX_VALUE);
        chartPanel.setOpaque(false);
        chartPanel.setPreferredSize(new Dimension(600, 300));
        chartPanel.setMinimumSize(new Dimension(200, 200));

        CardPanel card = new CardPanel(new BorderLayout(0, 6));
        card.add(text, BorderLayout.NORTH);
        card.add(chartPanel, BorderLayout.CENTER);
        add(card, BorderLayout.CENTER);

        sourcesTitle.setFont(sourcesTitle.getFont().deriveFont(Font.BOLD));
        sourcesRows.setOpaque(false);
        CardPanel sources = new CardPanel(new BorderLayout(0, 6));
        JPanel sourcesHeader = new JPanel(new BorderLayout());
        sourcesHeader.setOpaque(false);
        sourcesHeader.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        sourcesHeader.add(sourcesTitle, BorderLayout.WEST);
        sources.add(sourcesHeader, BorderLayout.NORTH);
        sources.add(sourcesRows, BorderLayout.CENTER);
        add(sources, BorderLayout.SOUTH);
    }

    void showProjection(String who, TrackerCombination combination, Projection projection, Double goal,
                        int windowDays) {
        String rules = combination.name() + " adds up " + String.join(", ", combination.parts()) + ". "
                + MODEL_RULES + (combination.includes(TrackerCombination.WALLET) ? TRANSFER_RULES : "");
        for (JLabel label : List.of(headlineLabel, detailLabel, goalLabel)) {
            label.setToolTipText(rules);
        }
        goalLabel.setText(" ");
        if (projection == null || projection.isEmpty()) {
            headlineLabel.setText("Nothing to forecast yet");
            detailLabel.setText(combination.usesSnapshots()
                    ? "EVE Farm saves the values of " + who + " each time it updates, and forecasts them once it has "
                    + "a few days of them."
                    : "Nothing earned in the last " + windowDays + " days from " + String.join(", ",
                    combination.parts()) + ".");
            chartPanel.setChart(null);
            chartPanel.setVisible(false);
            return;
        }
        chartPanel.setVisible(true);
        Instant end = projection.end();
        String endDay = day(end.minusSeconds(1));
        if (projection.pace() == null) {
            headlineLabel.setText(compact(projection.latest().total()) + " ISK in " + combination.name());
            detailLabel.setText("The forecast to " + endDay + " needs at least 3 days of data in the last "
                    + windowDays + " days.");
        } else {
            double gain = projection.expected() - projection.latest().total();
            headlineLabel.setText("About " + compact(projection.expected()) + " ISK on " + endDay + "  ("
                    + signed(gain) + ")");
            String detail = "At " + signed(projection.pace().perDay()) + " ISK a day, a usual day of the last "
                    + windowDays + " days.";
            if (!projection.band().isEmpty()) {
                detail += " " + CHANCE + " it ends between " + compact(projection.low()) + " and "
                        + compact(projection.high()) + ".";
            }
            detailLabel.setText(detail + (combination.includes(TrackerCombination.WALLET)
                    ? " Corporation wallet transfers and donations don't count." : ""));
        }
        if (goal != null) {
            goalLabel.setText(goalText(projection.goal(goal), endDay));
        }
        chartPanel.setChart(chart(combination.name(), projection, goal));
    }

    void showSources(Map<Source, Double> perDay, Set<Source> included, int windowDays) {
        sourcesRows.removeAll();
        sourcesTitle.setText("Where it comes from: ISK a day over the last " + windowDays + " days");
        double biggest = perDay.values().stream().mapToDouble(Math::abs).max().orElse(0);
        double smallest = Math.max(MIN_SHOWN_PER_DAY, biggest * MIN_SHOWN_SHARE);
        List<Map.Entry<Source, Double>> shown = perDay.entrySet().stream()
                .filter(entry -> Math.abs(entry.getValue()) >= smallest)
                .sorted(Map.Entry.<Source, Double>comparingByValue().reversed())
                .toList();
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        if (shown.isEmpty()) {
            c.insets = new Insets(2, 0, 8, 0);
            sourcesRows.add(UiColors.mutedLabel("Nothing earned in these days."), c);
        }
        double largest = shown.stream().mapToDouble(entry -> Math.abs(entry.getValue())).max().orElse(1);
        for (int i = 0; i < shown.size(); i++) {
            Map.Entry<Source, Double> entry = shown.get(i);
            Source source = entry.getKey();
            boolean last = i == shown.size() - 1;
            JLabel label = new JLabel(source.label() + (included.contains(source) ? "  ✓" : ""));
            label.setToolTipText(source.description() + (included.contains(source)
                    ? ". Added up in the forecast" : ". Not added up in the forecast"));
            JLabel value = new JLabel(signed(entry.getValue()));
            value.setForeground(entry.getValue() < 0 ? TableStyler.lossColor() : TableStyler.gainColor());
            value.setToolTipText(label.getToolTipText());
            c.insets = new Insets(2, 0, last ? 8 : 2, 16);
            c.gridx = 0;
            c.weightx = 0;
            sourcesRows.add(label, c);
            c.gridx = 1;
            c.anchor = GridBagConstraints.EAST;
            sourcesRows.add(value, c);
            c.gridx = 2;
            c.anchor = GridBagConstraints.WEST;
            sourcesRows.add(new Bar(Math.abs(entry.getValue()) / largest, entry.getValue() < 0), c);
            c.gridx = 3;
            c.weightx = 1;
            JLabel total = UiColors.mutedLabel(signed(entry.getValue() * windowDays) + " in " + windowDays + " days");
            total.setToolTipText(label.getToolTipText());
            sourcesRows.add(total, c);
            c.weightx = 0;
            c.gridy++;
        }
        sourcesRows.revalidate();
        sourcesRows.repaint();
    }

    private String goalText(Goal goal, String endDay) {
        String target = "Goal " + compact(goal.target()) + ": ";
        if (goal.reached()) {
            return target + "reached.";
        }
        String when;
        if (goal.expected() == null) {
            when = "not at this pace.";
        } else {
            when = "around " + day(goal.expected());
            if (goal.earliest() != null && goal.likely() != null) {
                when += ", likely between " + day(goal.earliest()) + " and " + day(goal.likely());
            } else if (goal.earliest() != null) {
                when += ", possibly from " + day(goal.earliest());
            }
            when += ".";
        }
        String needed = goal.neededPerDay() > 0 ? " To reach it by " + endDay + ": " + signed(goal.neededPerDay())
                + " ISK a day." : "";
        return target + when + needed;
    }

    private String day(Instant at) {
        LocalDate date = LocalDate.ofInstant(at, zone);
        return (date.getYear() == LocalDate.now(zone).getYear() ? DAY : DAY_AND_YEAR).format(date);
    }

    private static JFreeChart chart(String name, Projection projection, Double goal) {
        ChartStyle style = ChartStyle.current("Table.background");
        Color line = TrackerChartFactory.colorFor("Total", style.dark());
        Color mutedInk = style.mutedInk();
        Font axisFont = style.axisFont();

        XYSeries actual = new XYSeries(name, true, false);
        for (Point point : projection.history()) {
            actual.add(point.at().toEpochMilli(), point.total());
        }
        DateAxis domainAxis = new DateAxis(null);
        NumberAxis rangeAxis = new NumberAxis(null);
        rangeAxis.setNumberFormatOverride(new CompactIskNumberFormat());

        XYLineAndShapeRenderer actualRenderer = new XYLineAndShapeRenderer(true, false);
        actualRenderer.setDrawSeriesLineAsPath(true);
        actualRenderer.setSeriesPaint(0, line);
        actualRenderer.setSeriesStroke(0, ACTUAL_STROKE);
        actualRenderer.setDefaultToolTipGenerator(toolTips());
        XYPlot plot = new XYPlot(new XYSeriesCollection(actual), domainAxis, rangeAxis, actualRenderer);

        double lowest = projection.history().stream().mapToDouble(Point::total).min().orElse(0);
        double highest = projection.history().stream().mapToDouble(Point::total).max().orElse(0);
        if (projection.pace() != null) {
            Point latest = projection.latest();
            long endMillis = projection.end().toEpochMilli();
            YIntervalSeries ahead = new YIntervalSeries("Forecast");
            if (projection.band().isEmpty()) {
                ahead.add(latest.at().toEpochMilli(), latest.total(), latest.total(), latest.total());
                ahead.add(endMillis, projection.expected(), projection.expected(), projection.expected());
            } else {
                for (Band step : projection.band()) {
                    ahead.add(step.at().toEpochMilli(), step.expected(), step.low(), step.high());
                }
            }
            YIntervalSeriesCollection aheadData = new YIntervalSeriesCollection();
            aheadData.addSeries(ahead);
            DeviationRenderer aheadRenderer = new DeviationRenderer(true, false);
            aheadRenderer.setSeriesPaint(0, line);
            aheadRenderer.setSeriesStroke(0, PROJECTED_STROKE);
            aheadRenderer.setSeriesFillPaint(0, line);
            aheadRenderer.setAlpha(BAND_ALPHA);
            aheadRenderer.setDefaultToolTipGenerator(toolTips());
            plot.setDataset(1, aheadData);
            plot.setRenderer(1, aheadRenderer);
            lowest = Math.min(lowest, Math.min(projection.low(), projection.expected()));
            highest = Math.max(highest, Math.max(projection.high(), projection.expected()));

            XYTextAnnotation label = new XYTextAnnotation(compact(projection.expected()), endMillis,
                    projection.expected());
            label.setTextAnchor(TextAnchor.BOTTOM_RIGHT);
            label.setFont(style.uiFont().deriveFont(Font.BOLD));
            label.setPaint(line);
            plot.addAnnotation(label);

            ValueMarker today = new ValueMarker(latest.at().toEpochMilli(), mutedInk, MARKER_STROKE);
            today.setLabel("Today");
            today.setLabelFont(axisFont);
            today.setLabelPaint(mutedInk);
            today.setLabelAnchor(RectangleAnchor.TOP_LEFT);
            today.setLabelTextAnchor(TextAnchor.TOP_RIGHT);
            today.setLabelOffset(new RectangleInsets(4, 4, 4, 4));
            today.setLabelBackgroundColor(new Color(0, 0, 0, 0));
            plot.addDomainMarker(today);

            if (!projection.band().isEmpty()) {
                plot.addAnnotation(bandLabel(projection.high(), endMillis, TextAnchor.BOTTOM_RIGHT, axisFont,
                        mutedInk));
                plot.addAnnotation(bandLabel(projection.low(), endMillis, TextAnchor.TOP_RIGHT, axisFont,
                        mutedInk));
            }
        }
        if (goal != null && goal <= Math.max(highest, 1) * GOAL_IN_VIEW) {
            Color goalColor = UIManager.getColor("Component.accentColor");
            ValueMarker marker = new ValueMarker(goal, goalColor == null ? line.darker() : goalColor, GOAL_STROKE);
            marker.setLabel("Goal " + compact(goal));
            marker.setLabelFont(axisFont.deriveFont(Font.BOLD));
            marker.setLabelPaint(goalColor == null ? line.darker() : goalColor);
            marker.setLabelAnchor(RectangleAnchor.TOP_LEFT);
            marker.setLabelTextAnchor(TextAnchor.BOTTOM_LEFT);
            marker.setLabelOffset(new RectangleInsets(2, 6, 2, 6));
            marker.setLabelBackgroundColor(new Color(0, 0, 0, 0));
            plot.addRangeMarker(marker);
            highest = Math.max(highest, goal);
            lowest = Math.min(lowest, goal);
        }
        double margin = Math.max((highest - lowest) * 0.08, Math.abs(highest) * 0.01 + 1);
        rangeAxis.setRange(lowest - margin, highest + margin * 1.5);

        Instant first = projection.history().getFirst().at().minus(Duration.ofDays(1));
        Instant last = projection.pace() == null ? projection.latest().at().plus(Duration.ofDays(1))
                : projection.end();
        domainAxis.setRange(Date.from(first), Date.from(last));
        domainAxis.setTickUnit(tickUnit(Duration.between(first, last)));

        plot.setAxisOffset(RectangleInsets.ZERO_INSETS);
        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        style.apply(chart, plot, domainAxis, rangeAxis);
        chart.setPadding(new RectangleInsets(4, 0, 8, 4));
        return chart;
    }

    private static XYTextAnnotation bandLabel(double value, long x, TextAnchor anchor, Font font, Color paint) {
        XYTextAnnotation label = new XYTextAnnotation(compact(value), x, value);
        label.setTextAnchor(anchor);
        label.setFont(font);
        label.setPaint(paint);
        return label;
    }

    private static DateTickUnit tickUnit(Duration shown) {
        if (shown.toDays() <= 14) {
            return new DateTickUnit(DateTickUnitType.DAY, 1, new SimpleDateFormat("MMM d", Locale.US));
        }
        if (shown.toDays() <= 120) {
            return new DateTickUnit(DateTickUnitType.DAY, 7, new SimpleDateFormat("MMM d", Locale.US));
        }
        if (shown.toDays() <= 400) {
            return new DateTickUnit(DateTickUnitType.MONTH, 1, new SimpleDateFormat("MMM", Locale.US));
        }
        return new DateTickUnit(DateTickUnitType.MONTH, 3, new SimpleDateFormat("MMM yyyy", Locale.US));
    }

    private static StandardXYToolTipGenerator toolTips() {
        return new StandardXYToolTipGenerator(TOOLTIP_FORMAT, new SimpleDateFormat("EEE, MMM d", Locale.US),
                new CompactIskNumberFormat(2));
    }

    static String compact(double isk) {
        return new CompactIskNumberFormat().format(isk);
    }

    static String signed(double isk) {
        return (isk >= 0 ? "+" : "") + compact(isk);
    }

    private static final class Bar extends JComponent {

        private final double share;
        private final boolean loss;

        Bar(double share, boolean loss) {
            this.share = share;
            this.loss = loss;
            setPreferredSize(new Dimension(BAR_WIDTH, 10));
            setMinimumSize(new Dimension(40, 10));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Color color = loss ? TableStyler.lossColor() : TableStyler.gainColor();
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 150));
            int width = (int) Math.round(Math.min(getWidth(), BAR_WIDTH) * share);
            g.fillRoundRect(0, (getHeight() - 8) / 2, Math.max(width, 2), 8, 4, 4);
        }
    }
}
