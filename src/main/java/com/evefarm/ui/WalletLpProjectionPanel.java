package com.evefarm.ui;

import com.evefarm.service.WalletLpProjection.Point;
import com.evefarm.service.WalletLpProjection.Projection;
import com.formdev.flatlaf.FlatLaf;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.DateTickUnit;
import org.jfree.chart.axis.DateTickUnitType;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
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

final class WalletLpProjectionPanel extends JPanel {

    private static final DateTimeFormatter LAST_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);
    private static final String TOOLTIP_FORMAT = "{1}: {2} ISK";
    private static final Stroke ACTUAL_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke PROJECTED_STROKE = new BasicStroke(2.2f, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND, 10f, new float[]{6f, 5f}, 0f);
    private static final Stroke MARKER_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{2f, 3f}, 0f);
    private static final Stroke GRIDLINE_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{2f, 2f}, 0f);
    private static final float BAND_ALPHA = 0.16f;
    private static final String RULES = "Wallet balance plus the LP with your favorite corporation in the LP Store, "
            + "at your ISK/LP target. ISK moved from or to a corporation wallet and donations count as moved, "
            + "not produced. The pace is the trend line of the last 30 days; the band is the pace of the slowest "
            + "and the fastest week in them.";

    private final ZoneId zone = ZoneId.systemDefault();
    private final JLabel titleLabel = new JLabel(" ");
    private final JLabel headlineLabel = new JLabel(" ");
    private final JLabel detailLabel = new JLabel(" ");
    private final ChartPanel chartPanel = new ChartPanel(null, false);

    WalletLpProjectionPanel() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, titleLabel.getFont().getSize2D() + 3f));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
        add(titleLabel, BorderLayout.NORTH);

        headlineLabel.setFont(headlineLabel.getFont().deriveFont(Font.BOLD, headlineLabel.getFont().getSize2D() + 2f));
        detailLabel.setForeground(UiColors.muted());
        for (JLabel label : List.of(headlineLabel, detailLabel)) {
            label.setToolTipText(RULES);
        }
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

    void setProjection(String characterName, Projection projection) {
        LocalDate lastDay = LocalDate.ofInstant(projection.end().minusSeconds(1), zone);
        titleLabel.setText("Forecast for Favorite Character");
        titleLabel.setToolTipText(characterName + "'s wallet and LP, projected to " + LAST_DAY.format(lastDay));
        if (projection.isEmpty()) {
            headlineLabel.setText("No Tracker snapshots yet");
            detailLabel.setText("EVE Farm saves the wallet and LP of " + characterName
                    + " each time it updates, and projects them once it has a few days of them.");
            chartPanel.setChart(null);
            chartPanel.setVisible(false);
            return;
        }
        chartPanel.setVisible(true);
        if (projection.pace() == null) {
            headlineLabel.setText(compact(projection.latest().total()) + " ISK in wallet and LP");
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
            detailLabel.setText(detail + " Corporation wallet transfers and donations don't count.");
        }
        chartPanel.setChart(chart(projection));
    }

    private static JFreeChart chart(Projection projection) {
        boolean dark = FlatLaf.isLafDark();
        Color line = TrackerChartFactory.colorFor("Total", dark);
        Color background = UIManager.getColor("Table.background");
        if (background == null) {
            background = dark ? new Color(43, 43, 43) : Color.WHITE;
        }
        Color gridline = dark ? new Color(60, 60, 58) : new Color(0xe1, 0xe0, 0xd9);
        Color secondaryInk = dark ? new Color(0xc3, 0xc2, 0xb7) : new Color(0x52, 0x51, 0x4e);
        Color mutedInk = new Color(0x89, 0x87, 0x81);
        Color baseline = dark ? new Color(0x38, 0x38, 0x35) : new Color(0xc3, 0xc2, 0xb7);
        Font uiFont = UIManager.getFont("Label.font");
        if (uiFont == null) {
            uiFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        Font axisFont = uiFont.deriveFont(Font.PLAIN, uiFont.getSize2D() - 1f);

        XYSeries actual = new XYSeries("Wallet + LP", true, false);
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
            label.setFont(uiFont.deriveFont(Font.BOLD));
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

        plot.setBackgroundPaint(background);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(gridline);
        plot.setRangeGridlinePaint(gridline);
        plot.setDomainGridlineStroke(GRIDLINE_STROKE);
        plot.setRangeGridlineStroke(GRIDLINE_STROKE);
        plot.setAxisOffset(RectangleInsets.ZERO_INSETS);
        for (ValueAxis axis : List.of(domainAxis, rangeAxis)) {
            axis.setTickLabelFont(axisFont);
            axis.setTickLabelPaint(secondaryInk);
            axis.setTickMarksVisible(false);
        }
        domainAxis.setAxisLinePaint(baseline);
        rangeAxis.setAxisLineVisible(false);

        JFreeChart chart = new JFreeChart(null, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        chart.setBackgroundPaint(background);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
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
