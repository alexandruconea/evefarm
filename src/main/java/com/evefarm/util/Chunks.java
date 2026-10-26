package com.evefarm.util;

import java.util.ArrayList;
import java.util.List;

public final class Chunks {

    public static <T> List<List<T>> of(List<T> values, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Chunk size must be at least 1, was " + size);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(start + size, values.size())));
        }
        return chunks;
    }

    private Chunks() {
    }
}
