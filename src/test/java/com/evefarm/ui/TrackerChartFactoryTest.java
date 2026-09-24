package com.evefarm.ui;

import com.evefarm.model.TrackerSnapshot;
import org.jfree.data.time.Minute;
import org.jfree.data.time.TimeSeriesCollection;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackerChartFactoryTest {

    private final TrackerChartFactory factory = new TrackerChartFactory();
    private static final Instant MINUTE = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void totalSumsOnlyTheVisibleComponentSeries() {
        TrackerSnapshot snapshot = TrackerSnapshot.of(1L, MINUTE,
                 100.0,  50.0,  0,
                 0,  0,  0,
                 0,  0,  0,
                 0,  0,  0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(snapshot),
                Set.of("Total", "Wallet Balance"));

        double total = dataset.getSeries("Total").getValue(new Minute(Date.from(MINUTE))).doubleValue();
        assertEquals(100.0, total, "Total must only include the checked component (Wallet Balance)");
    }

    @Test
    void totalNeverIncludesEscrowToCover() {
        TrackerSnapshot snapshot = TrackerSnapshot.of(1L, MINUTE,
                100.0, 0, 0, 0, 0,  999.0, 0, 0, 0, 0, 0, 0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(snapshot),
                Set.of("Total", "Wallet Balance", "Escrows To Cover"));

        double total = dataset.getSeries("Total").getValue(new Minute(Date.from(MINUTE))).doubleValue();
        assertEquals(100.0, total, "Escrows To Cover is ISK not yet owned - must never count toward Total");
    }

    @Test
    void totalSumsAcrossEveryCharacterInTheSameMinute() {
        TrackerSnapshot char1 = TrackerSnapshot.of(1L, MINUTE, 100.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        TrackerSnapshot char2 = TrackerSnapshot.of(2L, MINUTE, 250.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(char1, char2),
                Set.of("Total", "Wallet Balance"));

        double total = dataset.getSeries("Total").getValue(new Minute(Date.from(MINUTE))).doubleValue();
        assertEquals(350.0, total);
    }

    @Test
    void onlyTheLatestSnapshotPerCharacterPerMinuteIsUsed() {
        TrackerSnapshot stale = TrackerSnapshot.of(1L, MINUTE, 100.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        TrackerSnapshot fresh = TrackerSnapshot.of(1L, MINUTE.plusSeconds(30), 999.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(stale, fresh),
                Set.of("Total", "Wallet Balance"));

        double total = dataset.getSeries("Total").getValue(new Minute(Date.from(MINUTE))).doubleValue();
        assertEquals(999.0, total, "the later snapshot within the same minute must win, not be summed with the earlier one");
    }

    @Test
    void aPointCoversEverySnapshotOfItsRoundForEveryCharacter() {
        TrackerSnapshot first = wallet(1L, MINUTE.plusSeconds(10), 100.0);
        TrackerSnapshot otherCharacter = wallet(2L, MINUTE.plusSeconds(20), 50.0);
        TrackerSnapshot retake = wallet(1L, MINUTE.plusSeconds(40), 120.0);
        TrackerSnapshot laterRound = wallet(1L, MINUTE.plusSeconds(3 * 3600), 130.0);

        List<TrackerSnapshot> atPoint = TrackerChartFactory.snapshotsAtPoint(
                List.of(first, retake, otherCharacter, laterRound), MINUTE);

        assertEquals(List.of(first, otherCharacter, retake), atPoint,
                "deleting a point must also remove the earlier retake, or it would take the deleted one's place");
    }

    @Test
    void aRoundThatCrossesAMinuteIsOnePointWithEveryCharacter() {
        Instant round = Instant.parse("2026-09-24T17:12:57Z");
        TrackerSnapshot barset = wallet(1L, round, 87.0);
        TrackerSnapshot castan = wallet(2L, round.plusSeconds(2), 2.0);
        TrackerSnapshot malpais = wallet(3L, round.plusSeconds(6), 146.0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(barset, castan, malpais),
                Set.of("Total", "Wallet Balance"));

        assertEquals(1, dataset.getSeries("Total").getItemCount(), "17:12 and 17:13 are the same snapshot round");
        assertEquals(235.0, dataset.getSeries("Total").getValue(0).doubleValue());
        assertEquals(List.of(barset, castan, malpais), TrackerChartFactory.snapshotsAtPoint(
                List.of(barset, castan, malpais), round));
    }

    @Test
    void aCharacterMissingFromARoundKeepsItsLastKnownValue() {
        TrackerSnapshot main = wallet(1L, MINUTE, 100.0);
        TrackerSnapshot alt = wallet(2L, MINUTE.plusSeconds(5), 50.0);
        TrackerSnapshot mainLater = wallet(1L, MINUTE.plusSeconds(3 * 3600), 120.0);

        TimeSeriesCollection dataset = factory.buildDataset(List.of(main, alt, mainLater),
                Set.of("Total", "Wallet Balance"));

        double later = dataset.getSeries("Total").getValue(new Minute(Date.from(mainLater.capturedAt()))).doubleValue();
        assertEquals(170.0, later, "a character that failed to update must not look like a drop to zero");
    }

    private static TrackerSnapshot wallet(long characterId, Instant capturedAt, double wallet) {
        return TrackerSnapshot.of(characterId, capturedAt, wallet, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
