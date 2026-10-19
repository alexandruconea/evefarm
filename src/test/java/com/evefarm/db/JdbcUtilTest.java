package com.evefarm.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcUtilTest {

    @Test
    void placeholdersHaveOneQuestionMarkPerValue() {
        assertEquals("?,?,?", JdbcUtil.placeholders(3));
        assertEquals("?", JdbcUtil.placeholders(1));
        assertEquals("", JdbcUtil.placeholders(0));
    }
}
