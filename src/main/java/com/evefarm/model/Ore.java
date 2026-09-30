package com.evefarm.model;

import java.util.Map;

public record Ore(
        int typeId,
        int portionSize,
        Integer compressedTypeId,
        Map<Integer, Long> materials
) {
}
