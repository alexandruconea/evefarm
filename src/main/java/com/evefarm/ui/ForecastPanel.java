package com.evefarm.ui;

import com.evefarm.service.TrackerCombination;
import com.evefarm.service.WalletLpProjection.Point;
import com.evefarm.service.WalletLpProjection.Projection;
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
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
import java.util.function.Consumer;

final class ForecastPanel extends JPanel {

    private static final DateTimeFormatter LAST_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final String TOOLTIP_FORMAT = "{1}: {2} ISK";
    private static final Stroke ACTUAL_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke PROJECTED_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f);
    private static final Stroke MARKER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{2f, 3f}, 0f);
    private static final float BAND_ALPHA = 0.16f;
    private static final String PACE_RULES = "The pace is the trend line of the last 30 days of Tracker snapshots; "
            + "the band is the pace of the slowest and the fastest week in them.";
    private static final String TRANSFER_RULES = " ISK moved from or to a corporation wallet and donations count "
            + "as moved, not produced.";

    private final ZoneId zone = ZoneId.systemDefault();
    private final JLabel titleLabel = new JLabel(" ");
    private final JComboBox<String> combinationCombo = new JComboBox<>();
    private final JButton editButton = new JButton("Edit...");
    private boolean fillingCombinations;
    private final JLabel headlineLabel = new JLabel(" ");
    private final JLabel detailLabel = new JLabel(" ");
    private final ChartPanel chartPanel = new ChartPanel(null, false);

    ForecastPanel(Consumer<String> onCombinationChosen, Runnable onEdit) {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 3f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        combinationCombo.setToolTipText("What the forecast adds up. Edit... chooses what each combination adds up.");
        ComboSizing.names(combinationCombo);
        ComboFilter.install(combinationCombo);
        combinationCombo.addActionListener(e -> {
            if (!fillingCombinations && combinationCombo.getSelectedItem() != null) {
                onCombinationChosen.accept(combinationCombo.getSelectedItem().toString());
            }
        });
        editButton.addActionListener(e -> onEdit.run());
        JPanel combination = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        combination.setOpaque(false);
        combination.add(new JLabel("Adds up:"));
        combination.add(combinationCombo);
        combination.add(editButton);
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(combination, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        headlineLabel.setFont(headlineLabel.getFont().deriveFont(Font.BOLD, headlineLabel.getFont().getSize2D() + 2f));
        detailLabel.setForeground(UiColors.muted());
        JPanel text = new JPanel(new BorderLayout(0, 3));
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        text.add(headlineLabel, BorderLayout.NORTH);
        text.add(detailLabel, BorderLayout.CENTER);

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
    }

    String combinationName() {
        Object selected = combinationCombo.getSelectedItem();
        return selected == null ? null : selected.toString();
    }

    void setCombinations(List<String> names, String selected) {
        fillingCombinations = true;
        try {
            combinationCombo.removeAllItems();
            names.forEach(combinationCombo::addItem);
            if (!names.contains(selected)) {
                combinationCombo.addItem(selected);
            }
            combinationCombo.setSelectedItem(selected);
        } finally {
            fillingCombinations = false;
        }
    }

    void setProjection(String characterName, TrackerCombination combination, Projection projection) {
        LocalDate lastDay = LocalDate.ofInstant(projection.end().minusSeconds(1), zone);
        boolean wallet = combination.includes(TrackerCombination.WALLET);
        String rules = combination.name() + " adds up " + String.join(", ", combination.parts()) + ". "
                + PACE_RULES + (wallet ? TRANSFER_RULES : "");
        for (JLabel label : List.of(headlineLabel, detailLabel)) {
            label.setToolTipText(rules);
        }
        titleLabel.setText("Forecast for Favorite Character");
        titleLabel.setToolTipText(characterName + "'s " + combination.name() + ", projected to "
                + LAST_DAY.format(lastDay));
        if (projection.isEmpty()) {
            headlineLabel.setText("No Tracker snapshots yet");
            detailLabel.setText("EVE Farm saves the values of " + characterName
                    + " each time it updates, and projects them once it has a few days of them.");
            chartPanel.setChart(null);
            chartPanel.setVisible(false);
            return;
        }
        chartPanel.setVisible(true);
        if (projection.pace() == null) {
            headlineLabel.setText(compact(projection.latest().total()) + " ISK in " + combination.name());
            detailLabel.setText("The projection to " + LAST_DAY.format(lastDay)
                    + " needs at least 3 days of Tracker snapshots.");
        } else {
            double gain = projection.expected() - projection.latest().total();
            headlineLabel.setText("About " + compact(projection.expected()) + " ISK on " + LAST_DAY.format(lastDay)
                    + "  (" + signed(gain) + ")");
            String detail = "At " + signed(projection.pace().perDay()) + " ISK a day, the trend of the last 30 days.";
            if (projection.pace().weeks() != null) {
                detail += " Between " + compact(projection.low()) + " and " + compact(projection.high())
                        + " at the pace of the slowest and the fastest week.";
            }
            detailLabel.setText(detail + (wallet ? " Corporation wallet transfers and donations don't count." : ""));
        }
        chartPanel.setChart(chart(combination.name(), projection));
    }

    private static JFreeChart chart(String name, Projection projection) {
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
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setNumberFormatOverride(new CompactIskNumberFormat());
        rangeAxis.setUpperMargin(0.12);

        XYLineAndShapeRenderer actualRenderer = new XYLineAndShapeRenderer(true, false);
        actualRenderer.setDrawSeriesLineAsPath(true);
        actualRenderer.setSeriesPaint(0, line);
        actualRenderer.setSeriesStroke(0, ACTUAL_STROKE);
        actualRenderer.setDefaultToolTipGenerator(toolTips());
        XYPlot plot = new XYPlot(new XYSeriesCollection(actual), domainAxis, rangeAxis, actualRenderer);

        if (projection.pace() != null) {
            Point latest = projection.latest();
            long endMillis = projection.end().toEpochMilli();
            YIntervalSeries ahead = new YIntervalSeries("Projection");
            ahead.add(latest.at().toEpochMilli(), latest.total(), latest.total(), latest.total());
            ahead.add(endMillis, projection.expected(), projection.low(), projection.high());
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

            if (projection.pace().weeks() != null) {
                plot.addAnnotation(bandLabel(projection.high(), endMillis, TextAnchor.BOTTOM_RIGHT, axisFont,
                        mutedInk));
                plot.addAnnotation(bandLabel(projection.low(), endMillis, TextAnchor.TOP_RIGHT, axisFont,
                        mutedInk));
            }
        }
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
        return new DateTickUnit(DateTickUnitType.MONTH, 1, new SimpleDateFormat("MMM", Locale.US));
    }

    private static StandardXYToolTipGenerator toolTips() {
        return new StandardXYToolTipGenerator(TOOLTIP_FORMAT, new SimpleDateFormat("EEE, MMM d", Locale.US),
                new CompactIskNumberFormat(2));
    }

    private static String compact(double isk) {
        return new CompactIskNumberFormat().format(isk);
    }

    private static String signed(double isk) {
        return (isk >= 0 ? "+" : "") + compact(isk);
    }
}
