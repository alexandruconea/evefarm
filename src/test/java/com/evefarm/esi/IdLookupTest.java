package com.evefarm.esi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IdLookupTest {

    private static Function<List<Long>, List<Long>> rejecting(List<Long> invalid, List<List<Long>> calls) {
        return ids -> {
            calls.add(List.copyOf(ids));
            if (ids.stream().anyMatch(invalid::contains)) {
                throw new EsiException(404, "{\"error\":\"Ensure all IDs are valid before resolving\"}");
            }
            return ids;
        };
    }

    @Test
    void anInvalidIdIsLeftOutAndEveryOtherIdIsLookedUp() {
        List<Long> ids = LongStream.rangeClosed(1, 100).boxed().toList();
        List<List<Long>> calls = new ArrayList<>();

        List<Long> found = IdLookup.skippingInvalidIds(ids, rejecting(List.of(42L), calls));

        assertEquals(ids.stream().filter(id -> id != 42L).toList(), found);
        assertEquals(15, calls.size(), "the batch is halved down to the invalid ID only");
    }

    @Test
    void aLookupWithoutInvalidIdsIsOneCall() {
        List<List<Long>> calls = new ArrayList<>();

        assertEquals(List.of(1L, 2L, 3L), IdLookup.skippingInvalidIds(List.of(1L, 2L, 3L),
                rejecting(List.of(), calls)));
        assertEquals(1, calls.size());
    }

    @Test
    void otherErrorsAreNotRetried() {
        List<List<Long>> calls = new ArrayList<>();

        assertThrows(EsiException.class, () -> IdLookup.skippingInvalidIds(List.of(1L, 2L), ids -> {
            calls.add(ids);
            throw new EsiException(504, "{\"error\":\"Timeout contacting tranquility\"}");
        }));
        assertEquals(1, calls.size());
    }

    @Test
    void itGivesUpBeforeSpendingTooMuchOfTheErrorLimit() {
        List<Long> ids = LongStream.rangeClosed(1, 64).boxed().toList();
        List<List<Long>> calls = new ArrayList<>();

        assertThrows(EsiException.class, () -> IdLookup.skippingInvalidIds(ids, rejecting(ids, calls)));
        assertEquals(IdLookup.MAX_REJECTED_LOOKUPS + 1, calls.size());
    }
}
