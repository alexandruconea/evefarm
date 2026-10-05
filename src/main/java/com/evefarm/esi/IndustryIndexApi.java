package com.evefarm.esi;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public final class IndustryIndexApi {

    private final EsiHttpClient client;

    public IndustryIndexApi(EsiHttpClient client) {
        this.client = client;
    }

    public Map<Long, Map<String, Double>> listCostIndices() {
        Map<Long, Map<String, Double>> result = new HashMap<>();
        for (JsonNode system : readTree(client.get("/industry/systems/", null, Map.of()))) {
            Map<String, Double> indices = new HashMap<>();
            for (JsonNode index : system.path("cost_indices")) {
                indices.put(index.path("activity").asText(), index.path("cost_index").asDouble());
            }
            result.put(system.path("solar_system_id").asLong(), indices);
        }
        return result;
    }

    private JsonNode readTree(String json) {
        try {
            return client.objectMapper().readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ESI industry response", e);
        }
    }
}
