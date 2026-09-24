package com.evefarm.ui;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYDataset;

import javax.swing.SwingUtilities;
import java.awt.Cursor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Rectangle2D;

final class ChartTimeNavigator extends MouseAdapter {

    private static final double ZOOM_STEP = 1.2;
    private static final double MIN_SPAN_MILLIS = 30 * 60 * 1000;

    private final ChartPanel chartPanel;
    private final Runnable onNavigate;
    private int pressedX = -1;
    private Range rangeAtPress;

    ChartTimeNavigator(ChartPanel chartPanel, Runnable onNavigate) {
        this.chartPanel = chartPanel;
        this.onNavigate = onNavigate;
    }

    void install() {
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.addMouseListener(this);
        chartPanel.addMouseMotionListener(this);
        chartPanel.addMouseWheelListener(this);
    }

    static Range zoom(Range current, double anchor, double factor) {
        return new Range(anchor - (anchor - current.getLowerBound()) * factor,
                anchor + (current.getUpperBound() - anchor) * factor);
    }

    static Range valuesAround(XYDataset dataset, Range window) {
        double lowest = Double.POSITIVE_INFINITY;
        double highest = Double.NEGATIVE_INFINITY;
        for (int series = 0; series < dataset.getSeriesCount(); series++) {
            int before = -1;
            int after = -1;
            for (int item = 0; item < dataset.getItemCount(series); item++) {
                double x = dataset.getXValue(series, item);
                double y = dataset.getYValue(series, item);
                if (x < window.getLowerBound()) {
                    before = item;
                } else if (x > window.getUpperBound()) {
                    after = after < 0 ? item : after;
                } else if (!Double.isNaN(y)) {
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                }
            }
            for (int neighbour : new int[]{before, after}) {
                double y = neighbour < 0 ? Double.NaN : dataset.getYValue(series, neighbour);
                if (!Double.isNaN(y)) {
                    lowest = Math.min(lowest, y);
                    highest = Math.max(highest, y);
                }
            }
        }
        if (lowest > highest) {
            return null;
        }
        double margin = highest > lowest ? (highest - lowest) * 0.05 : Math.max(1, Math.abs(highest) * 0.05);
        double lower = lowest >= 0 ? Math.max(0, lowest - margin) : lowest - margin;
        return new Range(lower, highest + margin);
    }

    static Range keepInside(Range range, Range full) {
        double length = Math.min(range.getLength(), full.getLength());
        double lower = Math.max(full.getLowerBound(), Math.min(range.getLowerBound(), full.getUpperBound() - length));
        return new Range(lower, lower + length);
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        Rectangle2D area = chartPanel.getScreenDataArea();
        Range full = fullRange();
        if (full == null || !area.contains(e.getPoint())) {
            return;
        }
        XYPlot plot = chartPanel.getChart().getXYPlot();
        ValueAxis axis = plot.getDomainAxis();
        double anchor = axis.java2DToValue(e.getX(), area, plot.getDomainAxisEdge());
        Range zoomed = zoom(axis.getRange(), anchor, Math.pow(ZOOM_STEP, e.getPreciseWheelRotation()));
        if (zoomed.getLength() >= full.getLength()) {
            axis.setAutoRange(true);
        } else if (zoomed.getLength() >= MIN_SPAN_MILLIS) {
            axis.setRange(keepInside(zoomed, full));
        }
        navigated();
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && chartPanel.getScreenDataArea().contains(e.getPoint())) {
            pressedX = e.getX();
            rangeAtPress = chartPanel.getChart().getXYPlot().getDomainAxis().getRange();
        }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        ValueAxis axis = chartPanel.getChart().getXYPlot().getDomainAxis();
        Range full = fullRange();
        if (pressedX < 0 || axis.isAutoRange() || full == null) {
            return;
        }
        chartPanel.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        double shift = (e.getX() - pressedX) / chartPanel.getScreenDataArea().getWidth() * rangeAtPress.getLength();
        axis.setRange(keepInside(Range.shift(rangeAtPress, -shift), full));
        navigated();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (pressedX >= 0) {
            pressedX = -1;
            chartPanel.setCursor(null);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2
                && chartPanel.getScreenDataArea().contains(e.getPoint())) {
            chartPanel.getChart().getXYPlot().getDomainAxis().setAutoRange(true);
            navigated();
        }
    }

    private void navigated() {
        fitValues();
        onNavigate.run();
    }

    void fitValues() {
        XYPlot plot = chartPanel.getChart().getXYPlot();
        ValueAxis values = plot.getRangeAxis();
        Range visible = plot.getDataset(1) == null || plot.getDomainAxis().isAutoRange()
                ? null : valuesAround(plot.getDataset(1), plot.getDomainAxis().getRange());
        if (visible == null) {
            values.setAutoRange(true);
        } else {
            values.setRange(visible);
        }
    }

    private Range fullRange() {
        XYPlot plot = chartPanel.getChart().getXYPlot();
        ValueAxis axis = plot.getDomainAxis();
        Range data = plot.getDataRange(axis);
        if (data == null || data.getLength() <= 0) {
            return null;
        }
        return Range.expand(data, axis.getLowerMargin(), axis.getUpperMargin());
    }
}
