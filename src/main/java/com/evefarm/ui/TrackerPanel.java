package com.evefarm.ui;

import com.evefarm.AppContext;
import com.evefarm.model.EveCharacter;
import com.evefarm.model.TrackerSnapshot;
import com.evefarm.util.DateUtil;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYAreaRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.renderer.xy.XYSplineRenderer;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;

import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class TrackerPanel extends javax.swing.JPanel {

    private static final Logger LOG = Logger.getLogger(TrackerPanel.class.getName());

    private final AppContext appContext;
    private final TrackerChartFactory chartFactory = new TrackerChartFactory();
    private final DefaultListModel<EveCharacter> characterListModel = new DefaultListModel<>();
    private final Map<String, JCheckBox> seriesCheckBoxes = new LinkedHashMap<>();
    private final AtomicInteger reloadGeneration = new AtomicInteger(0);

    private ChartPanel chartPanel;

    public TrackerPanel(AppContext appContext) {
        initComponents();
        this.appContext = appContext;
        postInit();
    }

    private void postInit() {
        buildChart();

        quickDateCombo.setModel(new DefaultComboBoxModel<>(new String[]{
                "1 Day", "1 Week", "2 Weeks", "1 Month", "3 Months", "6 Months", "1 Year", "2 Years"}));
        quickDateCombo.addActionListener(e -> applyQuickDate((String) quickDateCombo.getSelectedItem()));

        fromSpinner.setModel(new SpinnerDateModel());
        fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
        toSpinner.setModel(new SpinnerDateModel());
        toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));

        showButton.setIcon(Icons.EYE);
        showButton.addActionListener(e -> reloadChart());
        skillPointFiltersButton.setIcon(Icons.TARGET);
        skillPointFiltersButton.addActionListener(e ->
                new SkillPointFilterDialog(SwingUtilities.getWindowAncestor(this), appContext).setVisible(true));
        manageSnapshotsButton.setIcon(Icons.ARCHIVE);
        manageSnapshotsButton.addActionListener(e -> openSnapshotManager());

        seriesPanel.setLayout(new BoxLayout(seriesPanel, BoxLayout.Y_AXIS));
        for (String name : TrackerChartFactory.SERIES_NAMES) {
            JCheckBox checkBox = new JCheckBox(name, true);
            checkBox.addActionListener(e -> reloadChart());
            seriesCheckBoxes.put(name, checkBox);
            seriesPanel.add(checkBox);
        }

        allProfilesCheckBox.addActionListener(e -> {
            characterList.setEnabled(!allProfilesCheckBox.isSelected());
            reloadChart();
        });

        characterList.setModel(characterListModel);
        characterList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        characterList.setEnabled(false);
        characterList.setCellRenderer((list, value, index, isSelected, cellHasFocus) -> {
            JLabel label = new JLabel(value.characterName());
            label.setOpaque(true);
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        ListSelectionListener listener = e -> {
            if (!e.getValueIsAdjusting()) {
                reloadChart();
            }
        };
        characterList.addListSelectionListener(listener);

        quickDateCombo.setSelectedItem("1 Month");
        refreshCharacterFilter();
    }

    private void buildChart() {
        JFreeChart chart = ChartFactory.createTimeSeriesChart(
                null, "Date", null, new TimeSeriesCollection(), true, true, false);
        XYPlot plot = chart.getXYPlot();
        plot.setDomainAxis(new DateAxis("Date"));
        NumberAxis rangeAxis = new SparseNumberAxis();
        plot.setRangeAxis(rangeAxis);
        rangeAxis.setAutoRangeIncludesZero(false);
        rangeAxis.setNumberFormatOverride(new CompactIskNumberFormat());

        boolean dark = com.formdev.flatlaf.FlatLaf.isLafDark();
        Color background = javax.swing.UIManager.getColor("Panel.background");
        if (background == null) {
            background = dark ? new Color(43, 43, 43) : Color.WHITE;
        }
        Color foreground = javax.swing.UIManager.getColor("Label.foreground");
        if (foreground == null) {
            foreground = dark ? Color.LIGHT_GRAY : Color.BLACK;
        }
        Color gridlineColor = dark ? new Color(60, 60, 58) : new Color(0xe1, 0xe0, 0xd9);
        Stroke gridlineStroke = new BasicStroke(
                1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, new float[]{2f, 2f}, 0f);

        chart.setBackgroundPaint(background);
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        plot.setBackgroundPaint(background);
        plot.setOutlineVisible(false);
        plot.setDomainGridlinePaint(gridlineColor);
        plot.setRangeGridlinePaint(gridlineColor);
        plot.setDomainGridlineStroke(gridlineStroke);
        plot.setRangeGridlineStroke(gridlineStroke);
        plot.setRenderer(1, new XYSplineRenderer(3));

        Color secondaryInk = dark ? new Color(0xc3, 0xc2, 0xb7) : new Color(0x52, 0x51, 0x4e);
        Color mutedInk = new Color(0x89, 0x87, 0x81);
        Color baselineColor = dark ? new Color(0x38, 0x38, 0x35) : new Color(0xc3, 0xc2, 0xb7);
        Font uiFont = javax.swing.UIManager.getFont("Label.font");
        if (uiFont == null) {
            uiFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        }
        Font axisFont = uiFont.deriveFont(Font.PLAIN, uiFont.getSize2D() - 1f);
        for (ValueAxis axis : List.of(plot.getDomainAxis(), rangeAxis)) {
            axis.setTickLabelFont(axisFont);
            axis.setTickLabelPaint(secondaryInk);
            axis.setLabelFont(axisFont);
            axis.setLabelPaint(mutedInk);
            axis.setTickMarksVisible(false);
        }
        plot.getDomainAxis().setAxisLinePaint(baselineColor);
        rangeAxis.setAxisLineVisible(false);
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(background);
            chart.getLegend().setItemPaint(foreground);
            chart.getLegend().setItemFont(chart.getLegend().getItemFont().deriveFont(12f));
        }

        chartPanel = new ChartPanel(chart);
        chartPanel.setBackground(background);
        chartPanel.setPreferredSize(new Dimension(700, 500));

        chartContainerPanel.setLayout(new BorderLayout());
        chartContainerPanel.add(chartPanel, BorderLayout.CENTER);
    }

    private static final class SparseNumberAxis extends NumberAxis {
        @Override
        protected double estimateMaximumTickLabelHeight(Graphics2D g2) {
            return super.estimateMaximumTickLabelHeight(g2) * 2;
        }
    }

    private static final class FadingAreaRenderer extends XYAreaRenderer {
        private final Color topColor;

        FadingAreaRenderer(Color topColor) {
            super(XYAreaRenderer.AREA);
            this.topColor = topColor;
        }

        @Override
        public void drawItem(Graphics2D g2, XYItemRendererState state, Rectangle2D dataArea,
                              PlotRenderingInfo info, XYPlot plot, ValueAxis domainAxis, ValueAxis rangeAxis,
                              XYDataset dataset, int series, int item, CrosshairState crosshairState, int pass) {
            setSeriesPaint(series, new GradientPaint(
                    0f, (float) dataArea.getMinY(), withAlpha(topColor, 70),
                    0f, (float) dataArea.getMaxY(), withAlpha(topColor, 0)));
            super.drawItem(g2, state, dataArea, info, plot, domainAxis, rangeAxis, dataset, series, item,
                    crosshairState, pass);
        }

        private static Color withAlpha(Color color, int alpha) {
            return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
        }
    }

    public void onShown() {
        reloadChart();
    }

    public void refreshCharacterFilter() {
        characterListModel.clear();
        for (EveCharacter character : appContext.characterService.listCharacters()) {
            characterListModel.addElement(character);
        }
        if (!characterListModel.isEmpty()) {
            characterList.setSelectionInterval(0, characterListModel.size() - 1);
        }
        reloadChart();
    }

    private void applyQuickDate(String option) {
        LocalDate today = LocalDate.now();
        LocalDate from = switch (option) {
            case "1 Day" -> today.minusDays(1);
            case "1 Week" -> today.minusWeeks(1);
            case "2 Weeks" -> today.minusWeeks(2);
            case "3 Months" -> today.minusMonths(3);
            case "6 Months" -> today.minusMonths(6);
            case "1 Year" -> today.minusYears(1);
            case "2 Years" -> today.minusYears(2);
            default -> today.minusMonths(1);
        };
        fromSpinner.setValue(DateUtil.toDate(from));
        toSpinner.setValue(DateUtil.toDate(today));
        reloadChart();
    }

    private Set<Long> selectedCharacterIds() {
        if (allProfilesCheckBox.isSelected()) {
            return appContext.characterService.listCharacters().stream()
                    .map(EveCharacter::characterId)
                    .collect(Collectors.toSet());
        }
        return characterList.getSelectedValuesList().stream()
                .map(EveCharacter::characterId)
                .collect(Collectors.toSet());
    }

    private Set<String> visibleSeriesNames() {
        return seriesCheckBoxes.entrySet().stream()
                .filter(e -> e.getValue().isSelected())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private void reloadChart() {
        if (chartPanel == null) {
            return;
        }
        int generation = reloadGeneration.incrementAndGet();
        Set<Long> characterIds = selectedCharacterIds();
        Set<String> visibleSeries = visibleSeriesNames();
        Instant from = DateUtil.startOfDay(DateUtil.toLocalDate((Date) fromSpinner.getValue()));
        Instant to = DateUtil.endOfDay(DateUtil.toLocalDate((Date) toSpinner.getValue()));

        new SwingWorker<List<TrackerSnapshot>, Void>() {
            @Override
            protected List<TrackerSnapshot> doInBackground() {
                return appContext.snapshotDao.listBetween(characterIds, from, to);
            }

            @Override
            protected void done() {
                if (generation != reloadGeneration.get()) {
                    return;
                }
                try {
                    applyDataset(get(), visibleSeries);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to reload tracker chart", e);
                }
            }
        }.execute();
    }

    private static final Ellipse2D POINT_MARKER = new Ellipse2D.Double(-3, -3, 6, 6);
    private static final Stroke TOTAL_STROKE = new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke SOLID_STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DASHED_STROKE = new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0f, new float[]{7f, 5f}, 0f);

    private void applyDataset(List<TrackerSnapshot> snapshots, Set<String> visibleSeries) {
        TimeSeriesCollection dataset = chartFactory.buildDataset(snapshots, visibleSeries);
        boolean dark = com.formdev.flatlaf.FlatLaf.isLafDark();
        XYPlot plot = (XYPlot) chartPanel.getChart().getPlot();

        plot.setDataset(1, dataset);
        if (plot.getRenderer(1) instanceof XYSplineRenderer renderer) {
            renderer.setDefaultToolTipGenerator(new StandardXYToolTipGenerator(
                    "<html><b>{0}</b><br>{1}: {2} ISK</html>",
                    new SimpleDateFormat("MMM d, yyyy"), new DecimalFormat("#,##0")));
            for (int i = 0; i < dataset.getSeriesCount(); i++) {
                String name = String.valueOf(dataset.getSeriesKey(i));
                boolean isTotal = "Total".equals(name);
                renderer.setSeriesPaint(i, TrackerChartFactory.colorFor(name, dark));
                renderer.setSeriesStroke(i, isTotal ? TOTAL_STROKE
                        : TrackerChartFactory.isDashed(name) ? DASHED_STROKE : SOLID_STROKE);
                renderer.setSeriesShapesVisible(i, true);
                renderer.setSeriesShapesFilled(i, true);
                renderer.setSeriesShape(i, POINT_MARKER);
            }
        }

        TimeSeries totalSeries = null;
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            if ("Total".equals(dataset.getSeriesKey(i))) {
                totalSeries = dataset.getSeries(i);
            }
        }
        if (totalSeries != null) {
            TimeSeriesCollection totalDataset = new TimeSeriesCollection();
            totalDataset.addSeries(totalSeries);
            FadingAreaRenderer areaRenderer = new FadingAreaRenderer(TrackerChartFactory.colorFor("Total", dark));
            areaRenderer.setSeriesVisibleInLegend(0, false);
            plot.setDataset(0, totalDataset);
            plot.setRenderer(0, areaRenderer);
        } else {
            plot.setDataset(0, null);
        }
    }

    private void openSnapshotManager() {
        Set<Long> characterIds = selectedCharacterIds();
        Instant from = DateUtil.startOfDay(DateUtil.toLocalDate((Date) fromSpinner.getValue()));
        Instant to = DateUtil.endOfDay(DateUtil.toLocalDate((Date) toSpinner.getValue()));

        manageSnapshotsButton.setEnabled(false);
        new SwingWorker<List<TrackerSnapshot>, Void>() {
            @Override
            protected List<TrackerSnapshot> doInBackground() {
                return appContext.snapshotDao.listBetween(characterIds, from, to);
            }

            @Override
            protected void done() {
                manageSnapshotsButton.setEnabled(true);
                try {
                    new SnapshotManagerDialog(SwingUtilities.getWindowAncestor(TrackerPanel.this),
                            appContext, get(), TrackerPanel.this::reloadChart)
                            .setVisible(true);
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load snapshots for Manage Snapshots", e);
                }
            }
        }.execute();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox allProfilesCheckBox;
    private javax.swing.JLabel charactersLabel;
    private javax.swing.JList<EveCharacter> characterList;
    private javax.swing.JScrollPane characterScrollPane;
    private javax.swing.JPanel chartContainerPanel;
    private javax.swing.JLabel fromLabel;
    private javax.swing.JSpinner fromSpinner;
    private javax.swing.JComboBox<String> quickDateCombo;
    private javax.swing.JLabel quickDateLabel;
    private javax.swing.JLabel seriesLabel;
    private javax.swing.JPanel seriesPanel;
    private javax.swing.JScrollPane seriesScrollPane;
    private javax.swing.JButton manageSnapshotsButton;
    private javax.swing.JButton showButton;
    private javax.swing.JButton skillPointFiltersButton;
    private javax.swing.JPanel sidebarPanel;
    private javax.swing.JLabel toLabel;
    private javax.swing.JSpinner toSpinner;
    // End of variables declaration//GEN-END:variables

    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        chartContainerPanel = new javax.swing.JPanel();
        sidebarPanel = new javax.swing.JPanel();
        quickDateLabel = new javax.swing.JLabel();
        quickDateCombo = new javax.swing.JComboBox<>();
        fromLabel = new javax.swing.JLabel();
        fromSpinner = new javax.swing.JSpinner();
        toLabel = new javax.swing.JLabel();
        toSpinner = new javax.swing.JSpinner();
        showButton = new javax.swing.JButton();
        skillPointFiltersButton = new javax.swing.JButton();
        manageSnapshotsButton = new javax.swing.JButton();
        seriesLabel = new javax.swing.JLabel();
        seriesScrollPane = new javax.swing.JScrollPane();
        seriesPanel = new javax.swing.JPanel();
        charactersLabel = new javax.swing.JLabel();
        allProfilesCheckBox = new javax.swing.JCheckBox();
        characterScrollPane = new javax.swing.JScrollPane();
        characterList = new javax.swing.JList<>();

        javax.swing.GroupLayout chartContainerPanelLayout = new javax.swing.GroupLayout(chartContainerPanel);
        chartContainerPanel.setLayout(chartContainerPanelLayout);
        chartContainerPanelLayout.setHorizontalGroup(
            chartContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 700, Short.MAX_VALUE)
        );
        chartContainerPanelLayout.setVerticalGroup(
            chartContainerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 500, Short.MAX_VALUE)
        );

        quickDateLabel.setText("Quick Date");

        fromLabel.setText("From");

        toLabel.setText("To");

        showButton.setText("Show");

        skillPointFiltersButton.setText("Skill Point Filters...");

        manageSnapshotsButton.setText("Manage Snapshots...");

        seriesLabel.setText("Series");

        javax.swing.GroupLayout seriesPanelLayout = new javax.swing.GroupLayout(seriesPanel);
        seriesPanel.setLayout(seriesPanelLayout);
        seriesPanelLayout.setHorizontalGroup(
            seriesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 220, Short.MAX_VALUE)
        );
        seriesPanelLayout.setVerticalGroup(
            seriesPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 150, Short.MAX_VALUE)
        );

        seriesScrollPane.setViewportView(seriesPanel);

        charactersLabel.setText("Characters");

        allProfilesCheckBox.setText("All Profiles");
        allProfilesCheckBox.setSelected(true);

        characterScrollPane.setViewportView(characterList);

        javax.swing.GroupLayout sidebarPanelLayout = new javax.swing.GroupLayout(sidebarPanel);
        sidebarPanel.setLayout(sidebarPanelLayout);
        sidebarPanelLayout.setHorizontalGroup(
            sidebarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sidebarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(sidebarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(quickDateLabel)
                    .addComponent(quickDateCombo, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(fromLabel)
                    .addComponent(fromSpinner, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(toLabel)
                    .addComponent(toSpinner, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(showButton, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(skillPointFiltersButton, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(manageSnapshotsButton, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(seriesLabel)
                    .addComponent(seriesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE)
                    .addComponent(charactersLabel)
                    .addComponent(allProfilesCheckBox)
                    .addComponent(characterScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE))
                .addContainerGap())
        );
        sidebarPanelLayout.setVerticalGroup(
            sidebarPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(sidebarPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(quickDateLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(quickDateCombo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(fromLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fromSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(toLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(toSpinner, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(showButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(skillPointFiltersButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(manageSnapshotsButton)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(seriesLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(seriesScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(charactersLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(allProfilesCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(characterScrollPane, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(chartContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 700, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(sidebarPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(chartContainerPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
            .addComponent(sidebarPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 500, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents
}
