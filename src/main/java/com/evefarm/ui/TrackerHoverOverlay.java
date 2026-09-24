package com.evefarm.ui;

import com.evefarm.util.DateUtil;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.panel.AbstractOverlay;
import org.jfree.chart.panel.Overlay;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;

import javax.swing.Timer;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class TrackerHoverOverlay extends AbstractOverlay implements Overlay {

    private static final int REVEAL_MILLIS = 500;
    private static final int BOX_GAP = 14;
    private static final int PADDING = 10;
    private static final int ROW_GAP = 4;
    private static final int DOT = 8;
    private static final int ARC = 10;

    private final CompactIskNumberFormat valueFormat = new CompactIskNumberFormat(2);
    private int item = -1;
    private int mouseY;
    private double reveal = 1;
    private Timer revealTimer;

    private record Row(String name, String value, Color color) {
    }

    void hover(int item, int mouseY) {
        if (item != this.item || (item >= 0 && mouseY != this.mouseY)) {
            this.item = item;
            this.mouseY = mouseY;
            fireOverlayChanged();
        }
    }

    void clearHover() {
        hover(-1, 0);
    }

    void startReveal() {
        if (revealTimer != null) {
            revealTimer.stop();
        }
        long start = System.nanoTime();
        reveal = 0;
        revealTimer = new Timer(15, e -> {
            double progress = Math.min(1, (System.nanoTime() - start) / 1_000_000.0 / REVEAL_MILLIS);
            reveal = 1 - Math.pow(1 - progress, 3);
            if (progress >= 1) {
                ((Timer) e.getSource()).stop();
            }
            fireOverlayChanged();
        });
        revealTimer.start();
        fireOverlayChanged();
    }

    @Override
    public void paintOverlay(Graphics2D g2, ChartPanel chartPanel) {
        Rectangle2D area = chartPanel.getScreenDataArea();
        if (area == null || area.isEmpty() || chartPanel.getChart() == null) {
            return;
        }
        XYPlot plot = chartPanel.getChart().getXYPlot();
        Graphics2D g = (Graphics2D) g2.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (reveal < 1) {
                double revealed = area.getWidth() * reveal;
                g.setPaint(plot.getBackgroundPaint());
                g.fill(new Rectangle2D.Double(area.getX() + revealed, area.getY(),
                        area.getWidth() - revealed + 1, area.getHeight()));
            } else if (item >= 0) {
                paintHover(g, plot, area);
            }
        } finally {
            g.dispose();
        }
    }

    private void paintHover(Graphics2D g, XYPlot plot, Rectangle2D area) {
        if (!(plot.getDataset(1) instanceof TimeSeriesCollection dataset) || dataset.getSeriesCount() == 0
                || item >= dataset.getItemCount(0)) {
            return;
        }
        double x = plot.getDomainAxis().valueToJava2D(dataset.getXValue(0, item), area, plot.getDomainAxisEdge());
        if (x < area.getMinX() || x > area.getMaxX()) {
            return;
        }
        boolean dark = com.formdev.flatlaf.FlatLaf.isLafDark();
        Color background = plot.getBackgroundPaint() instanceof Color color ? color : Color.WHITE;
        Color foreground = uiColor("Label.foreground", dark ? Color.LIGHT_GRAY : Color.BLACK);

        Shape clip = g.getClip();
        g.clip(area);
        g.setColor(withAlpha(foreground, 90));
        g.setStroke(new BasicStroke(1f));
        g.draw(new Line2D.Double(x, area.getMinY(), x, area.getMaxY()));
        List<Row> rows = new ArrayList<>();
        for (int series = 0; series < dataset.getSeriesCount(); series++) {
            double value = dataset.getYValue(series, item);
            if (Double.isNaN(value)) {
                continue;
            }
            String name = String.valueOf(dataset.getSeriesKey(series));
            Color color = TrackerChartFactory.colorFor(name, dark);
            rows.add(new Row(name, valueFormat.format(value), color));
            double y = plot.getRangeAxis().valueToJava2D(value, area, plot.getRangeAxisEdge());
            g.setColor(background);
            g.fill(new Ellipse2D.Double(x - 5.5, y - 5.5, 11, 11));
            g.setColor(color);
            g.fill(new Ellipse2D.Double(x - 4, y - 4, 8, 8));
        }
        g.setClip(clip);

        String title = DateUtil.format(Instant.ofEpochMilli(
                dataset.getSeries(0).getTimePeriod(item).getFirstMillisecond()));
        paintBox(g, area, x, title, rows, dark, background, foreground);
    }

    private void paintBox(Graphics2D g, Rectangle2D area, double x, String title, List<Row> rows, boolean dark,
                          Color background, Color foreground) {
        Font font = UIManager.getFont("Label.font");
        if (font == null) {
            font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        Font bold = font.deriveFont(Font.BOLD);
        FontMetrics metrics = g.getFontMetrics(font);
        FontMetrics boldMetrics = g.getFontMetrics(bold);
        int rowHeight = metrics.getHeight();
        int nameWidth = 0;
        int valueWidth = 0;
        for (Row row : rows) {
            nameWidth = Math.max(nameWidth, metrics.stringWidth(row.name()));
            valueWidth = Math.max(valueWidth, boldMetrics.stringWidth(row.value()));
        }
        int width = 2 * PADDING + Math.max(boldMetrics.stringWidth(title), DOT + 6 + nameWidth + 18 + valueWidth);
        int height = 2 * PADDING + rowHeight + ROW_GAP + rows.size() * rowHeight;

        double boxX = x + BOX_GAP;
        if (boxX + width > area.getMaxX()) {
            boxX = Math.max(area.getMinX(), x - BOX_GAP - width);
        }
        double boxY = Math.max(area.getMinY(), Math.min(mouseY - height / 2.0, area.getMaxY() - height));

        g.setColor(new Color(0, 0, 0, dark ? 90 : 28));
        g.fill(new RoundRectangle2D.Double(boxX + 1, boxY + 2, width, height, ARC, ARC));
        RoundRectangle2D box = new RoundRectangle2D.Double(boxX, boxY, width, height, ARC, ARC);
        g.setColor(dark ? blend(background, Color.WHITE, 0.08) : Color.WHITE);
        g.fill(box);
        g.setColor(withAlpha(foreground, dark ? 70 : 45));
        g.setStroke(new BasicStroke(1f));
        g.draw(box);

        g.setFont(bold);
        g.setColor(foreground);
        g.drawString(title, (float) (boxX + PADDING), (float) (boxY + PADDING + metrics.getAscent()));
        double rowY = boxY + PADDING + rowHeight + ROW_GAP;
        for (Row row : rows) {
            g.setColor(row.color());
            g.fill(new Ellipse2D.Double(boxX + PADDING, rowY + (rowHeight - DOT) / 2.0, DOT, DOT));
            g.setColor(foreground);
            g.setFont(font);
            g.drawString(row.name(), (float) (boxX + PADDING + DOT + 6), (float) (rowY + metrics.getAscent()));
            g.setFont(bold);
            g.drawString(row.value(), (float) (boxX + width - PADDING - boldMetrics.stringWidth(row.value())),
                    (float) (rowY + metrics.getAscent()));
            rowY += rowHeight;
        }
    }

    private static Color uiColor(String key, Color fallback) {
        Color color = UIManager.getColor(key);
        return color == null ? fallback : color;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color blend(Color base, Color over, double amount) {
        return new Color(
                (int) Math.round(base.getRed() + (over.getRed() - base.getRed()) * amount),
                (int) Math.round(base.getGreen() + (over.getGreen() - base.getGreen()) * amount),
                (int) Math.round(base.getBlue() + (over.getBlue() - base.getBlue()) * amount));
    }
}
