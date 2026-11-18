package com.evefarm.ui;

import com.formdev.flatlaf.FlatLaf;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.XYPlot;

import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;
import java.util.List;

record ChartStyle(boolean dark, Color background, Color gridline, Color secondaryInk, Color mutedInk,
                  Color baseline, Font uiFont, Font axisFont) {

    static final Stroke GRIDLINE_STROKE = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, new float[]{2f, 2f}, 0f);

    static ChartStyle current(String backgroundKey) {
        boolean dark = FlatLaf.isLafDark();
        Color background = UIManager.getColor(backgroundKey);
        if (background == null) {
            background = dark ? new Color(43, 43, 43) : Color.WHITE;
        }
        Font uiFont = UIManager.getFont("Label.font");
        if (uiFont == null) {
            uiFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        return new ChartStyle(dark, background,
                dark ? new Color(60, 60, 58) : new Color(0xe1, 0xe0, 0xd9),
                dark ? new Color(0xc3, 0xc2, 0xb7) : new Color(0x52, 0x51, 0x4e),
                new Color(0x89, 0x87, 0x81),
                dark ? new Color(0x38, 0x38, 0x35) : new Color(0xc3, 0xc2, 0xb7),
                uiFont, uiFont.deriveFont(Font.PLAIN, uiFont.getSize2D() - 1f));
    }

    void apply(JFreeChart chart, XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis) {
        chart.setBackgroundPaint(background);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        plot.setBackgroundPaint(background);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(gridline);
        plot.setRangeGridlinePaint(gridline);
        plot.setDomainGridlineStroke(GRIDLINE_STROKE);
        plot.setRangeGridlineStroke(GRIDLINE_STROKE);
        for (ValueAxis axis : List.of(domainAxis, rangeAxis)) {
            axis.setTickLabelFont(axisFont);
            axis.setTickLabelPaint(secondaryInk);
            axis.setLabelFont(axisFont);
            axis.setLabelPaint(mutedInk);
            axis.setTickMarksVisible(false);
        }
        domainAxis.setAxisLinePaint(baseline);
        rangeAxis.setAxisLineVisible(false);
    }
}
