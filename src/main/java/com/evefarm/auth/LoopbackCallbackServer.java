package com.evefarm.auth;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class LoopbackCallbackServer implements AutoCloseable {

    private final HttpServer server;
    private final CompletableFuture<String> authorizationCode = new CompletableFuture<>();

    public LoopbackCallbackServer(int port, String expectedState) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not bind local callback server on port " + port
                            + ". Make sure no other application is using it.", e);
        }
        server.createContext("/callback", exchange -> {
            String body;
            try {
                body = handleCallback(exchange.getRequestURI(), expectedState);
            } catch (Exception e) {
                body = page("Invalid request", "This request was ignored. Finish the login in the EVE window.");
            }

            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            if (authorizationCode.isDone()) {
                server.stop(1);
            }
        });
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    public String awaitAuthorizationCode(long timeout, TimeUnit unit) {
        try {
            return authorizationCode.get(timeout, unit);
        } catch (Exception e) {
            throw new IllegalStateException("Failed while waiting for OAuth callback", e);
        }
    }

    private String handleCallback(URI requestUri, String expectedState) {
        Map<String, String> query = parseQuery(requestUri);
        String state = query.get("state");
        String code = query.get("code");
        String error = query.get("error");

        if (state == null || !state.equals(expectedState)) {
            return page("Invalid request", "This request was ignored. Finish the login in the EVE window.");
        }
        if (error != null) {
            authorizationCode.completeExceptionally(
                    new IllegalStateException("EVE SSO returned an error: " + error));
            return page("Login failed", escapeHtml(error) + "<br>You may close this window.");
        }
        if (code == null) {
            authorizationCode.completeExceptionally(
                    new IllegalStateException("No authorization code in callback."));
            return page("Login failed", "Missing code. You may close this window.");
        }
        authorizationCode.complete(code);
        return page("Login successful", "You may close this window and return to EVE Farm.");
    }

    private static String page(String title, String message) {
        return "<html><body><h3>" + title + "</h3><p>" + message + "</p></body></html>";
    }

    static String escapeHtml(String text) {
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    private static Map<String, String> parseQuery(URI uri) {
        String rawQuery = uri.getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        Map<String, String> result = new java.util.HashMap<>();
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String key = java.net.URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
