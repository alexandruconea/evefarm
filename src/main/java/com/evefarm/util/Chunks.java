package com.evefarm.util;

import java.util.ArrayList;
import java.util.List;

public final class Chunks {

    public static <T> List<List<T>> of(List<T> values, int size) {
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < values.size(); start += size) {
            chunks.add(values.subList(start, Math.min(start + size, values.size())));
        }
        return chunks;
    }

    private Chunks() {
    }
}
