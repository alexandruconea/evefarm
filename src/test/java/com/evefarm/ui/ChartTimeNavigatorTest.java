package com.evefarm.ui;

import org.jfree.data.Range;
import org.jfree.data.xy.DefaultXYDataset;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChartTimeNavigatorTest {

    @Test
    void zoomingInKeepsThePointUnderTheMouseInPlace() {
        Range zoomed = ChartTimeNavigator.zoom(new Range(0, 100), 80, 0.5);

        assertEquals(new Range(40, 90), zoomed);
        assertEquals(0.8, (80 - zoomed.getLowerBound()) / zoomed.getLength(), 1e-9,
                "the anchor stays at 80% of the width, like before the zoom");
    }

    @Test
    void aZoomedWindowIsMovedBackInsideTheData() {
        Range full = new Range(0, 100);

        assertEquals(new Range(0, 20), ChartTimeNavigator.keepInside(new Range(-15, 5), full));
        assertEquals(new Range(80, 100), ChartTimeNavigator.keepInside(new Range(90, 110), full));
        assertEquals(new Range(30, 50), ChartTimeNavigator.keepInside(new Range(30, 50), full));
    }

    @Test
    void aWindowWiderThanTheDataShowsAllOfIt() {
        assertEquals(new Range(0, 100), ChartTimeNavigator.keepInside(new Range(-50, 150), new Range(0, 100)));
    }

    @Test
    void aZoomBetweenTwoSnapshotsStillScalesToTheLineCrossingIt() {
        DefaultXYDataset dataset = new DefaultXYDataset();
        dataset.addSeries("Total", new double[][]{{0, 10, 20, 30}, {900, 50, 7, 900}});

        Range values = ChartTimeNavigator.valuesAround(dataset, new Range(11, 19));

        assertEquals(7 - 43 * 0.05, values.getLowerBound(), 1e-9);
        assertEquals(50 + 43 * 0.05, values.getUpperBound(), 1e-9,
                "only the neighbours of the empty window count, not the far-away 900s");
    }

    @Test
    void theValueAxisNeverDropsBelowZeroForIskAmounts() {
        DefaultXYDataset dataset = new DefaultXYDataset();
        dataset.addSeries("Escrows", new double[][]{{0, 10}, {0, 0}});
        dataset.addSeries("Total", new double[][]{{0, 10}, {200, 240}});

        assertEquals(0, ChartTimeNavigator.valuesAround(dataset, new Range(0, 10)).getLowerBound());
    }

    @Test
    void tooltipValuesShowTwoDecimalsWithoutTrailingZeros() {
        CompactIskNumberFormat tooltip = new CompactIskNumberFormat(2);

        assertEquals("147.24B", tooltip.format(147_239_000_000d));
        assertEquals("5B", tooltip.format(5_000_000_000d));
        assertEquals("1.5M", tooltip.format(1_500_000d));
        assertEquals("150B", new CompactIskNumberFormat().format(150_000_000_000d));
    }
}
