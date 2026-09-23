package com.evefarm.esi;

import com.evefarm.esi.dto.FuzzworkAggregateDto;
import com.evefarm.esi.dto.FuzzworkBlueprintDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class FuzzworkApi {

    private static final String BASE_URL = "https://market.fuzzwork.co.uk/aggregates/";
    private static final String BLUEPRINT_URL = "https://www.fuzzwork.co.uk/blueprint/api/blueprint.php";
    private static final String STATIC_DATA_CSV_URL = "https://www.fuzzwork.co.uk/dump/latest/csv/";
    private static final int JITA_4_4_STATION_ID = 60003760;
    private static final int BATCH_SIZE = 200;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<Integer, FuzzworkAggregateDto> fetchAggregates(List<Integer> typeIds) {
        Map<Integer, FuzzworkAggregateDto> result = new HashMap<>();
        for (int start = 0; start < typeIds.size(); start += BATCH_SIZE) {
            List<Integer> batch = typeIds.subList(start, Math.min(start + BATCH_SIZE, typeIds.size()));
            result.putAll(fetchBatch(batch));
        }
        return result;
    }

    private Map<Integer, FuzzworkAggregateDto> fetchBatch(List<Integer> typeIds) {
        String typesParam = typeIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String url = BASE_URL + "?station=" + JITA_4_4_STATION_ID + "&types=" + typesParam;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Fuzzwork request failed: HTTP " + response.statusCode());
            }
            Map<String, FuzzworkAggregateDto> parsed = objectMapper.readValue(response.body(),
                    new TypeReference<Map<String, FuzzworkAggregateDto>>() {
                    });
            Map<Integer, FuzzworkAggregateDto> result = new HashMap<>();
            for (Map.Entry<String, FuzzworkAggregateDto> entry : parsed.entrySet()) {
                try {
                    result.put(Integer.parseInt(entry.getKey()), entry.getValue());
                } catch (NumberFormatException ignored) {
                }
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reach Fuzzwork Market Data", e);
        }
    }

    public Optional<FuzzworkBlueprintDto> fetchBlueprintMaterials(int blueprintTypeId) {
        String url = BLUEPRINT_URL + "?typeid=" + blueprintTypeId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                return Optional.empty();
            }
            return Optional.ofNullable(objectMapper.readValue(response.body(), FuzzworkBlueprintDto.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String fetchStaticDataCsv(String fileName) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(STATIC_DATA_CSV_URL + fileName))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Fuzzwork static data request failed: HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reach Fuzzwork static data dump for " + fileName, e);
        }
    }
}
