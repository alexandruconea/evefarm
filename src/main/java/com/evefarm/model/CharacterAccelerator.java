package com.evefarm.model;

import java.time.Instant;

public record CharacterAccelerator(
        long characterId,
        Integer typeId,
        String name,
        int bonus,
        Instant firstSeen,
        Instant endsAt,
        boolean setByUser
) {

    public CharacterAccelerator withEnd(Instant end, boolean byUser) {
        return new CharacterAccelerator(characterId, typeId, name, bonus, firstSeen, end, byUser);
    }
}
