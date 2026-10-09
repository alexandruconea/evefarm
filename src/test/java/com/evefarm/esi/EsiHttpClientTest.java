package com.evefarm.esi;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EsiHttpClientTest {

    private record Reply(int status, Map<String, String> headers, String body) {
    }

    private record Seen(String method, String path, String query, String compatibilityDate, String authorization) {
    }

    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Seen> seen = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> waits = new ArrayList<>();
    private HttpServer server;
    private EsiHttpClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            seen.add(new Seen(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestURI().getQuery(), exchange.getRequestHeaders().getFirst("X-Compatibility-Date"),
                    exchange.getRequestHeaders().getFirst("Authorization")));
            Reply reply = replies.removeFirst();
            reply.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        client = new EsiHttpClient("http://127.0.0.1:" + server.getAddress().getPort(), waits::add);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void requestsCarryTheCompatibilityDateInsteadOfAVersionInThePath() {
        replies.add(new Reply(200, Map.of(), "{\"players\":1}"));

        String body = client.get("/status/", "token", Map.of("type_id", "34"));

        assertEquals("{\"players\":1}", body);
        assertEquals(new Seen("GET", "/status/", "type_id=34", EsiConfig.COMPATIBILITY_DATE, "Bearer token"),
                seen.getFirst());
    }

    @Test
    void everyPageIsFetched() {
        replies.add(new Reply(200, Map.of("X-Pages", "3"), "[1]"));
        replies.add(new Reply(200, Map.of("X-Pages", "3"), "[2]"));
        replies.add(new Reply(200, Map.of("X-Pages", "3"), "[3]"));

        assertEquals(List.of("[1]", "[2]", "[3]"), client.getAllPages("/markets/10000002/orders/", null, Map.of()));
        assertEquals(List.of("page=1", "page=2", "page=3"), seen.stream().map(Seen::query).toList());
    }

    @Test
    void aRateLimitedRequestIsRetriedAfterTheWaitEsiAsksFor() {
        replies.add(new Reply(429, Map.of("Retry-After", "7"), ""));
        replies.add(new Reply(200, Map.of(), "[]"));

        assertEquals("[]", client.get("/markets/prices/", null, Map.of()));
        assertEquals(List.of(7), waits);
        assertEquals(2, seen.size());
    }

    @Test
    void whenTheErrorBudgetRunsLowTheNextRequestWaitsForTheReset() {
        replies.add(new Reply(200, Map.of("X-Esi-Error-Limit-Remain", "3", "X-Esi-Error-Limit-Reset", "9"), "[]"));
        replies.add(new Reply(200, Map.of("X-Esi-Error-Limit-Remain", "100", "X-Esi-Error-Limit-Reset", "60"), "[]"));

        client.get("/markets/prices/", null, Map.of());
        client.get("/markets/prices/", null, Map.of());

        assertEquals(List.of(9), waits);
    }

    @Test
    void anErrorAnswerIsReportedWithItsStatus() {
        replies.add(new Reply(404, Map.of(), "{\"error\":\"Type not found\"}"));

        EsiException error = assertThrows(EsiException.class,
                () -> client.get("/universe/types/1/", null, Map.of()));

        assertEquals(404, error.statusCode());
    }

    @Test
    void retryAfterIsHonouredWithinSaneBounds() {
        assertEquals(12, EsiHttpClient.retryAfterSeconds(Optional.of("12")));
        assertEquals(5, EsiHttpClient.retryAfterSeconds(Optional.empty()), "no header -> default wait");
        assertEquals(5, EsiHttpClient.retryAfterSeconds(Optional.of("Wed, 21 Oct 2026 07:28:00 GMT")),
                "an HTTP-date isn't parsed -> default wait");
        assertEquals(60, EsiHttpClient.retryAfterSeconds(Optional.of("3600")), "never block a thread for an hour");
        assertEquals(1, EsiHttpClient.retryAfterSeconds(Optional.of("0")));
    }
}
