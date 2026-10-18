package com.evefarm.esi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

public final class IdLookup {

    private static final int INVALID_IDS = 404;
    static final int MAX_REJECTED_LOOKUPS = 30;

    public static <T> List<T> skippingInvalidIds(List<Long> ids, Function<List<Long>, List<T>> lookup) {
        List<T> found = new ArrayList<>();
        Deque<List<Long>> pending = new ArrayDeque<>();
        pending.add(ids);
        int rejected = 0;
        while (!pending.isEmpty()) {
            List<Long> batch = pending.removeFirst();
            try {
                found.addAll(lookup.apply(batch));
            } catch (EsiException e) {
                rejected++;
                if (e.statusCode() != INVALID_IDS || rejected > MAX_REJECTED_LOOKUPS) {
                    throw e;
                }
                if (batch.size() > 1) {
                    int middle = batch.size() / 2;
                    pending.addFirst(batch.subList(middle, batch.size()));
                    pending.addFirst(batch.subList(0, middle));
                }
            }
        }
        return found;
    }

    private IdLookup() {
    }
}
