package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CsvParsingTest {

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
        assertEquals(List.of("3008416", "22", "1000002", "60000004"),
                CsvParsing.parseLine("3008416,22,1000002,60000004"));
    }

    @Test
    void quotedFieldsHaveTheirSurroundingQuotesStripped() {
        assertEquals(List.of("3008416", "60000004"),
                CsvParsing.parseLine("\"3008416\",\"60000004\""));
    }

    @Test
    void aCommaInsideQuotesDoesNotSplitTheField() {
        assertEquals(List.of("25", "Industrialist - Entrepreneur",
                        "These pilots are masters of arbitrage, profiteering, and generally making a space-buck",
                        "Chief Advisor"),
                CsvParsing.parseLine(
                        "\"25\",\"Industrialist - Entrepreneur\",\"These pilots are masters of arbitrage, profiteering, and generally making a space-buck\",\"Chief Advisor\""));
    }

    @Test
    void anEscapedDoubleQuoteInsideAQuotedFieldBecomesALiteralQuote() {
        assertEquals(List.of("Muvolailen 10 - Moon 3 - \"CBD\" Storage"),
                CsvParsing.parseLine("\"Muvolailen 10 - Moon 3 - \"\"CBD\"\" Storage\""));
    }

    @Test
    void aTrailingEmptyFieldIsPreserved() {
        assertEquals(List.of("3008416", "22", ""),
                CsvParsing.parseLine("3008416,22,"));
    }
}
