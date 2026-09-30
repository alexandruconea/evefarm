package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CsvParsingTest {

    private static List<String> line(String text) {
        return CsvParsing.parseRecords(text).getFirst();
    }

    @Test
    void parseRecordsKeepsAQuotedNewlineInsideItsField() {
        String csv = "﻿\"id\",\"description\"\r\n\"1\",\"first line\r\nsecond, with comma\"\r\n\"2\",\"say \"\"hi\"\"\"\r\n";
        assertEquals(List.of(
                List.of("id", "description"),
                List.of("1", "first line\r\nsecond, with comma"),
                List.of("2", "say \"hi\"")
        ), CsvParsing.parseRecords(csv));
    }

    @Test
    void parseRecordsSkipsBlankLinesAndHandlesAMissingFinalNewline() {
        assertEquals(List.of(List.of("a", "b"), List.of("1", "")), CsvParsing.parseRecords("a,b\n\n1,"));
    }

    @Test
    void splitsAPlainUnquotedLine() {
        assertEquals(List.of("3008416", "22", "1000002", "60000004"), line("3008416,22,1000002,60000004"));
    }

    @Test
    void quotedFieldsHaveTheirSurroundingQuotesStripped() {
        assertEquals(List.of("3008416", "60000004"), line("\"3008416\",\"60000004\""));
    }

    @Test
    void aCommaInsideQuotesDoesNotSplitTheField() {
        assertEquals(List.of("25", "Industrialist - Entrepreneur",
                        "These pilots are masters of arbitrage, profiteering, and generally making a space-buck",
                        "Chief Advisor"),
                line("\"25\",\"Industrialist - Entrepreneur\",\"These pilots are masters of arbitrage, profiteering, "
                        + "and generally making a space-buck\",\"Chief Advisor\""));
    }

    @Test
    void anEscapedDoubleQuoteInsideAQuotedFieldBecomesALiteralQuote() {
        assertEquals(List.of("Muvolailen 10 - Moon 3 - \"CBD\" Storage"),
                line("\"Muvolailen 10 - Moon 3 - \"\"CBD\"\" Storage\""));
    }

    @Test
    void aTrailingEmptyFieldIsPreserved() {
        assertEquals(List.of("3008416", "22", ""), line("3008416,22,"));
    }

    @Test
    void aTableFindsItsColumnsByNameAndReadsNumbers() {
        CsvTable table = CsvTable.parse("﻿typeID,typeName,portionSize,security,marketGroupID\n"
                + "1230,Veldspar,100,0.94,None\n");
        List<String> row = table.rows().getFirst();

        assertEquals(1230, table.integer(row, "typeID"));
        assertEquals("Veldspar", table.text(row, "typeName"));
        assertEquals(100L, table.number(row, "portionSize"));
        assertEquals(0.94, table.decimal(row, "security"));
        assertNull(table.text(row, "marketGroupID"), "the SDE writes None for an empty value");
        assertNull(table.text(row, "noSuchColumn"));
    }
}
