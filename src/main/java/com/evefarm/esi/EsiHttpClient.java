package com.evefarm.esi;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class EsiHttpClient {

    private static final Logger LOG = Logger.getLogger(EsiHttpClient.class.getName());
    private static final int LOW_ERROR_BUDGET_THRESHOLD = 5;
    private static final int DEFAULT_RETRY_AFTER_SECONDS = 5;
    private static final int MAX_RETRY_AFTER_SECONDS = 60;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger errorLimitRemain = new AtomicInteger(100);
    private final AtomicInteger errorLimitResetSeconds = new AtomicInteger(0);

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public String get(String path, String accessTokenOrNull, Map<String, String> query) {
        return send(buildRequest("GET", path, accessTokenOrNull, query, null));
    }

    public String postJson(String path, String accessTokenOrNull, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            return send(buildRequest("POST", path, accessTokenOrNull, Map.of(), json));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialize ESI request body", e);
        }
    }

    public List<String> getAllPages(String path, String accessTokenOrNull, Map<String, String> query) {
        List<String> pages = new ArrayList<>();
        Map<String, String> pageQuery = new java.util.HashMap<>(query);
        pageQuery.put("page", "1");
        HttpResponse<String> first = sendRaw(buildRequest("GET", path, accessTokenOrNull, pageQuery, null));
        pages.add(first.body());

        int totalPages = first.headers().firstValue("X-Pages").map(Integer::parseInt).orElse(1);
        for (int page = 2; page <= totalPages; page++) {
            pageQuery.put("page", String.valueOf(page));
            HttpResponse<String> response = sendRaw(buildRequest("GET", path, accessTokenOrNull, pageQuery, null));
            pages.add(response.body());
        }
        return pages;
    }

    private HttpRequest buildRequest(String method, String path, String accessTokenOrNull,
                                      Map<String, String> query, String jsonBodyOrNull) {
        String queryString = query.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        String base = EsiConfig.BASE_URL + path + "?datasource=" + EsiConfig.DATASOURCE;
        String url = queryString.isBlank() ? base : base + "&" + queryString;

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", EsiConfig.USER_AGENT)
                .header("Accept", "application/json");
        if (accessTokenOrNull != null) {
            builder.header("Authorization", "Bearer " + accessTokenOrNull);
        }
        if ("POST".equals(method)) {
            builder.header("Content-Type", "application/json");
            builder.POST(HttpRequest.BodyPublishers.ofString(jsonBodyOrNull == null ? "" : jsonBodyOrNull));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private String send(HttpRequest request) {
        return sendRaw(request).body();
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        backoffIfBudgetLow();
        HttpResponse<String> response = sendOnce(request);

        if (response.statusCode() == 420) {
            LOG.warning("ESI error limit hit (420); backing off for " + errorLimitResetSeconds.get() + "s");
            sleepSeconds(errorLimitResetSeconds.get());
            response = sendOnce(request);
        } else if (response.statusCode() == 429) {
            int waitSeconds = retryAfterSeconds(response.headers().firstValue("Retry-After"));
            LOG.warning("ESI rate limit hit (429); retrying in " + waitSeconds + "s");
            sleepSeconds(waitSeconds);
            response = sendOnce(request);
        }

        if (response.statusCode() / 100 != 2) {
            throw new EsiException(response.statusCode(), response.body());
        }
        return response;
    }

    private HttpResponse<String> sendOnce(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            trackErrorBudget(response);
            return response;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to reach ESI at " + request.uri(), e);
        }
    }

    static int retryAfterSeconds(Optional<String> retryAfterHeader) {
        try {
            int seconds = retryAfterHeader.map(String::trim).map(Integer::parseInt)
                    .orElse(DEFAULT_RETRY_AFTER_SECONDS);
            return Math.max(1, Math.min(seconds, MAX_RETRY_AFTER_SECONDS));
        } catch (NumberFormatException e) {
            return DEFAULT_RETRY_AFTER_SECONDS;
        }
    }

    private void trackErrorBudget(HttpResponse<String> response) {
        response.headers().firstValue("X-Esi-Error-Limit-Remain")
                .map(Integer::parseInt).ifPresent(errorLimitRemain::set);
        response.headers().firstValue("X-Esi-Error-Limit-Reset")
                .map(Integer::parseInt).ifPresent(errorLimitResetSeconds::set);
    }

    private void backoffIfBudgetLow() {
        if (errorLimitRemain.get() < LOW_ERROR_BUDGET_THRESHOLD && errorLimitResetSeconds.get() > 0) {
            LOG.log(Level.WARNING, "ESI error budget low ({0}); pausing {1}s",
                    new Object[]{errorLimitRemain.get(), errorLimitResetSeconds.get()});
            sleepSeconds(errorLimitResetSeconds.get());
        }
    }

    private static void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(Duration.ofSeconds(seconds));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
