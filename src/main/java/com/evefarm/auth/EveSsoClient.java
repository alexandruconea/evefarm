package com.evefarm.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class EveSsoClient {

    private static final String AUTHORIZE_URL = "https://login.eveonline.com/v2/oauth/authorize/";
    private static final String TOKEN_URL = "https://login.eveonline.com/v2/oauth/token";
    private static final int HTTP_BAD_REQUEST = 400;
    private static final String INVALID_GRANT = "invalid_grant";
    private static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public URI buildAuthorizeUrl(OAuthConfig config, String state, String codeChallenge) {
        String scope = String.join(" ", OAuthConfig.SCOPES);
        String query = "response_type=code"
                + "&redirect_uri=" + encode(config.redirectUri())
                + "&client_id=" + encode(config.clientId())
                + "&scope=" + encode(scope)
                + "&code_challenge=" + encode(codeChallenge)
                + "&code_challenge_method=S256"
                + "&state=" + encode(state);
        return URI.create(AUTHORIZE_URL + "?" + query);
    }

    public TokenResponse exchangeAuthorizationCode(OAuthConfig config, String code, String codeVerifier) {
        String body = "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&client_id=" + encode(config.clientId())
                + "&code_verifier=" + encode(codeVerifier);
        return postToken(body);
    }

    public TokenResponse refreshAccessToken(OAuthConfig config, String refreshToken) {
        String body = "grant_type=refresh_token"
                + "&refresh_token=" + encode(refreshToken)
                + "&client_id=" + encode(config.clientId());
        return postToken(body);
    }

    private TokenResponse postToken(String formBody) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw failure(response.statusCode(), response.body());
            }
            return JSON.readValue(response.body(), TokenResponse.class);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("Failed to reach EVE SSO token endpoint", e);
        }
    }

    static IllegalStateException failure(int statusCode, String body) {
        if (statusCode == HTTP_BAD_REQUEST && INVALID_GRANT.equals(errorCode(body))) {
            return new LoginRejectedException();
        }
        return new IllegalStateException("EVE SSO token request failed: HTTP " + statusCode);
    }

    private static String errorCode(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            return JSON.readTree(body).path("error").asText("");
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
