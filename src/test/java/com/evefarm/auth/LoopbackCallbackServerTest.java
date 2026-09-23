package com.evefarm.auth;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopbackCallbackServerTest {

    private final HttpClient client = HttpClient.newHttpClient();

    private String get(LoopbackCallbackServer server, String query) throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + server.port() + "/callback?" + query);
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Test
    void aRequestWithTheWrongStateIsIgnoredAndTheLoginKeepsWaiting() throws Exception {
        try (LoopbackCallbackServer server = new LoopbackCallbackServer(0, "expected-state")) {
            String ignored = get(server, "state=wrong&error=" + encode("<script>alert(1)</script>"));

            assertTrue(ignored.contains("ignored"));
            assertFalse(ignored.contains("<script>"));

            get(server, "state=expected-state&code=abc123");
            assertEquals("abc123", server.awaitAuthorizationCode(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void anErrorFromEveIsShownEscapedAndFailsTheLogin() throws Exception {
        try (LoopbackCallbackServer server = new LoopbackCallbackServer(0, "expected-state")) {
            String page = get(server, "state=expected-state&error=" + encode("<img src=x onerror=alert(1)>"));

            assertFalse(page.contains("<img"));
            assertTrue(page.contains("&lt;img src=x onerror=alert(1)&gt;"));
            assertThrows(IllegalStateException.class, () -> server.awaitAuthorizationCode(5, TimeUnit.SECONDS));
        }
    }
}
