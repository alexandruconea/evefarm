package com.evefarm.auth;

import java.util.List;

public record CharacterIdentity(
        long characterId,
        String characterName,
        List<String> scopes,
        String ownerHash
) {
}
