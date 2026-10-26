package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunksTest {

    @Test
    void aListIsSplitIntoChunksOfAtMostTheGivenSize() {
        assertEquals(List.of(List.of(1, 2), List.of(3, 4), List.of(5)), Chunks.of(List.of(1, 2, 3, 4, 5), 2));
        assertEquals(List.of(List.of(1, 2)), Chunks.of(List.of(1, 2), 500));
        assertEquals(List.of(), Chunks.of(List.of(), 500));
    }

    @Test
    void aChunkSizeBelowOneIsRejectedInsteadOfLoopingForever() {
        assertThrows(IllegalArgumentException.class, () -> Chunks.of(List.of(1, 2), 0));
        assertThrows(IllegalArgumentException.class, () -> Chunks.of(List.of(1, 2), -1));
    }
}
