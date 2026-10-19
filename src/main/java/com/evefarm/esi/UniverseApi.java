package com.evefarm.esi;

import com.evefarm.esi.dto.StationDto;
import com.evefarm.esi.dto.StructureDto;
import com.evefarm.esi.dto.UniverseCategoryDto;
import com.evefarm.esi.dto.UniverseGroupDto;
import com.evefarm.esi.dto.UniverseNameDto;
import com.evefarm.esi.dto.UniverseTypeDto;
import com.evefarm.util.Chunks;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UniverseApi {

    private static final String RESPONSE = "ESI universe response";
    private static final int NAMES_BATCH_SIZE = 1000;

    private final EsiHttpClient client;

    public UniverseApi(EsiHttpClient client) {
        this.client = client;
    }

    public UniverseTypeDto getType(int typeId) {
        String body = client.get("/universe/types/" + typeId + "/", null, Map.of());
        return client.read(body, UniverseTypeDto.class, RESPONSE);
    }

    public UniverseGroupDto getGroup(int groupId) {
        String body = client.get("/universe/groups/" + groupId + "/", null, Map.of());
        return client.read(body, UniverseGroupDto.class, RESPONSE);
    }

    public UniverseCategoryDto getCategory(int categoryId) {
        String body = client.get("/universe/categories/" + categoryId + "/", null, Map.of());
        return client.read(body, UniverseCategoryDto.class, RESPONSE);
    }

    public StationDto getStation(long stationId) {
        String body = client.get("/universe/stations/" + stationId + "/", null, Map.of());
        return client.read(body, StationDto.class, RESPONSE);
    }

    public StructureDto getStructure(long structureId, String accessToken) {
        String body = client.get("/universe/structures/" + structureId + "/", accessToken, Map.of());
        return client.read(body, StructureDto.class, RESPONSE);
    }

    public Optional<Long> resolveSolarSystemId(String systemName) {
        String body = client.postJson("/universe/ids/", null, List.of(systemName));
        JsonNode systems = client.readTree(body, RESPONSE).path("systems");
        for (JsonNode system : systems) {
            if (systemName.equalsIgnoreCase(system.path("name").asText())) {
                return Optional.of(system.path("id").asLong());
            }
        }
        return Optional.empty();
    }

    public List<Long> getAsteroidBeltIds(long systemId) {
        String body = client.get("/universe/systems/" + systemId + "/", null, Map.of());
        JsonNode system = client.readTree(body, RESPONSE);
        List<Long> beltIds = new ArrayList<>();
        for (JsonNode planet : system.path("planets")) {
            for (JsonNode belt : planet.path("asteroid_belts")) {
                beltIds.add(belt.asLong());
            }
        }
        return beltIds;
    }

    public String getAsteroidBeltName(long beltId) {
        String body = client.get("/universe/asteroid_belts/" + beltId + "/", null, Map.of());
        return client.readTree(body, RESPONSE).path("name").asText();
    }

    public List<UniverseNameDto> resolveNames(List<Long> ids) {
        List<UniverseNameDto> result = new ArrayList<>();
        for (List<Long> batch : Chunks.of(ids, NAMES_BATCH_SIZE)) {
            String body = client.postJson("/universe/names/", null, batch);
            result.addAll(client.read(body, new TypeReference<List<UniverseNameDto>>() {
            }, RESPONSE));
        }
        return result;
    }
}
