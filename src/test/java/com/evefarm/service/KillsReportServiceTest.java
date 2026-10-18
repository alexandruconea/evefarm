package com.evefarm.service;

import com.evefarm.model.KillDayTypeRow;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillsReportServiceTest {

    private static final String LONG_NAME = "Dread Guristas Shatterer of the Fleet Without End and Far Beyond";

    @TempDir
    Path folder;

    @Test
    void aLongReportGoesOnToMorePagesWithTheTableHeaderRepeated() throws IOException {
        LocalDate first = LocalDate.of(2026, 9, 1);
        List<KillDayTypeRow> rows = IntStream.range(0, 80)
                .mapToObj(day -> new KillDayTypeRow(first.plusDays(day % 40), "Guristas Pirates",
                        day % 2 == 0 ? "Pithi Despoiler" : LONG_NAME, "TXW-EI", 1 + day % 5))
                .toList();
        Path report = folder.resolve("kills.pdf");

        new KillsReportService().generateReport(report, new KillsReportService.ReportFilters("Malpais Legate",
                "Guristas Pirates", "All systems", first, first.plusDays(39)), rows);

        try (PDDocument document = Loader.loadPDF(report.toFile())) {
            String text = new PDFTextStripper().getText(document);
            assertTrue(document.getNumberOfPages() >= 2, "80 rows don't fit on one page");
            assertTrue(text.contains("Kills Report"));
            assertTrue(text.contains("Malpais Legate"));
            assertTrue(text.contains("Page 2"));
            assertTrue(text.contains("Pithi Despoiler"));
            assertFalse(text.contains(LONG_NAME), "a name too long for its column is cut short");
            assertTrue(text.split("Date", -1).length - 1 >= 2, "the table header is repeated on the next page");
        }
    }
}
