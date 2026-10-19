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
        String body = client.get("/industry/systems/", null, Map.of());
        for (JsonNode system : client.readTree(body, "ESI industry response")) {
            Map<String, Double> indices = new HashMap<>();
            for (JsonNode index : system.path("cost_indices")) {
                indices.put(index.path("activity").asText(), index.path("cost_index").asDouble());
            }
            result.put(system.path("solar_system_id").asLong(), indices);
        }
        return result;
    }
}
