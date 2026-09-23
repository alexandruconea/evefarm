package com.evefarm.esi;

import com.evefarm.esi.dto.StationDto;
import com.evefarm.esi.dto.StructureDto;
import com.evefarm.esi.dto.UniverseCategoryDto;
import com.evefarm.esi.dto.UniverseGroupDto;
import com.evefarm.esi.dto.UniverseNameDto;
import com.evefarm.esi.dto.UniverseTypeDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class UniverseApi {

    private static final int NAMES_BATCH_SIZE = 1000;

    private final EsiHttpClient client;

    public UniverseApi(EsiHttpClient client) {
        this.client = client;
    }

    public UniverseTypeDto getType(int typeId) {
        String body = client.get("/universe/types/" + typeId + "/", null, Map.of());
        return read(body, UniverseTypeDto.class);
    }

    public UniverseGroupDto getGroup(int groupId) {
        String body = client.get("/universe/groups/" + groupId + "/", null, Map.of());
        return read(body, UniverseGroupDto.class);
    }

    public UniverseCategoryDto getCategory(int categoryId) {
        String body = client.get("/universe/categories/" + categoryId + "/", null, Map.of());
        return read(body, UniverseCategoryDto.class);
    }

    public StationDto getStation(long stationId) {
        String body = client.get("/universe/stations/" + stationId + "/", null, Map.of());
        return read(body, StationDto.class);
    }

    public StructureDto getStructure(long structureId, String accessToken) {
        String body = client.get("/universe/structures/" + structureId + "/", accessToken, Map.of());
        return read(body, StructureDto.class);
    }

    public Optional<Long> resolveSolarSystemId(String systemName) {
        String body = client.postJson("/universe/ids/", null, List.of(systemName));
        JsonNode systems = readTree(body).path("systems");
        for (JsonNode system : systems) {
            if (systemName.equalsIgnoreCase(system.path("name").asText())) {
                return Optional.of(system.path("id").asLong());
            }
        }
        return Optional.empty();
    }

    public List<Long> getAsteroidBeltIds(long systemId) {
        JsonNode system = readTree(client.get("/universe/systems/" + systemId + "/", null, Map.of()));
        List<Long> beltIds = new ArrayList<>();
        for (JsonNode planet : system.path("planets")) {
            for (JsonNode belt : planet.path("asteroid_belts")) {
                beltIds.add(belt.asLong());
            }
        }
        return beltIds;
    }

    public String getAsteroidBeltName(long beltId) {
        return readTree(client.get("/universe/asteroid_belts/" + beltId + "/", null, Map.of())).path("name").asText();
    }

    public List<UniverseNameDto> resolveNames(List<Long> ids) {
        List<UniverseNameDto> result = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += NAMES_BATCH_SIZE) {
            List<Long> batch = ids.subList(start, Math.min(start + NAMES_BATCH_SIZE, ids.size()));
            String body = client.postJson("/universe/names/", null, batch);
            result.addAll(readList(body, new TypeReference<List<UniverseNameDto>>() {
            }));
        }
        return result;
    }

    private JsonNode readTree(String json) {
        try {
            return client.objectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI universe response", e);
        }
    }

    private <T> T read(String json, Class<T> type) {
        try {
            return client.objectMapper().readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI universe response", e);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        try {
            return client.objectMapper().readValue(json, typeReference);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI universe response", e);
        }
    }
}
