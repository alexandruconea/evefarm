package com.evefarm.esi;

import com.evefarm.esi.dto.JaniceItemDto;
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
import java.util.stream.Collectors;

public final class JaniceApi {

    private static final String BASE_URL = "https://janice.e-351.com/api/rest/v2/pricer";
    private static final int JITA_MARKET_ID = 2;
    private static final int BATCH_SIZE = 100;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<Integer, JaniceItemDto> fetchPrices(List<Integer> typeIds, String apiKey) {
        Map<Integer, JaniceItemDto> result = new HashMap<>();
        for (int start = 0; start < typeIds.size(); start += BATCH_SIZE) {
            List<Integer> batch = typeIds.subList(start, Math.min(start + BATCH_SIZE, typeIds.size()));
            result.putAll(fetchBatch(batch, apiKey));
        }
        return result;
    }

    private Map<Integer, JaniceItemDto> fetchBatch(List<Integer> typeIds, String apiKey) {
        String body = typeIds.stream().map(String::valueOf).collect(Collectors.joining("\r\n"));
        String url = BASE_URL + "?market=" + JITA_MARKET_ID;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .header("X-ApiKey", apiKey)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException(
                        "Janice request failed: HTTP " + response.statusCode() + " - " + snippet(response.body()));
            }
            List<JaniceItemDto> items = objectMapper.readValue(response.body(),
                    new TypeReference<List<JaniceItemDto>>() {
                    });
            Map<Integer, JaniceItemDto> result = new HashMap<>();
            for (JaniceItemDto item : items) {
                if (item.itemType() != null) {
                    result.put(item.itemType().eid(), item);
                }
            }
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to reach Janice", e);
        }
    }

    static String snippet(String body) {
        String oneLine = body == null ? "" : body.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= 200 ? oneLine : oneLine.substring(0, 200) + "...";
    }
}
