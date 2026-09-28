package com.evefarm.esi;

import com.evefarm.esi.dto.CharacterLocationDto;
import com.evefarm.esi.dto.CharacterShipDto;

import java.util.Map;

public final class LocationApi {

    private final EsiHttpClient client;

    public LocationApi(EsiHttpClient client) {
        this.client = client;
    }

    public CharacterLocationDto getLocation(long characterId, String accessToken) {
        return read(client.get("/characters/" + characterId + "/location/", accessToken, Map.of()),
                CharacterLocationDto.class);
    }

    public CharacterShipDto getShip(long characterId, String accessToken) {
        return read(client.get("/characters/" + characterId + "/ship/", accessToken, Map.of()),
                CharacterShipDto.class);
    }

    private <T> T read(String body, Class<T> type) {
        try {
            return client.objectMapper().readValue(body, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse the ESI response", e);
        }
    }
}
