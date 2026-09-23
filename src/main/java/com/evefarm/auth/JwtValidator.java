package com.evefarm.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.net.URI;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class JwtValidator {

    private static final String JWKS_URL = "https://login.eveonline.com/oauth/jwks";
    private static final String EXPECTED_ISSUER = "https://login.eveonline.com";
    private static final String AUDIENCE_LITERAL = "EVE Online";

    private final JwkProvider jwkProvider;

    public JwtValidator() {
        try {
            this.jwkProvider = new UrlJwkProvider(URI.create(JWKS_URL).toURL());
        } catch (java.net.MalformedURLException e) {
            throw new IllegalStateException("Invalid JWKS URL", e);
        }
    }

    public CharacterIdentity validate(String accessTokenJwt, String expectedClientId) {
        try {
            DecodedJWT unverified = JWT.decode(accessTokenJwt);
            Jwk jwk = jwkProvider.get(unverified.getKeyId());
            Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

            DecodedJWT decoded = JWT.require(algorithm)
                    .withIssuer(EXPECTED_ISSUER, "login.eveonline.com")
                    .build()
                    .verify(accessTokenJwt);

            List<String> audience = decoded.getAudience() == null ? List.of() : decoded.getAudience();
            if (!audience.contains(expectedClientId) || !audience.contains(AUDIENCE_LITERAL)) {
                throw new IllegalStateException("JWT audience does not match this application's client id");
            }

            String subject = decoded.getSubject();
            long characterId = parseCharacterId(subject);
            String characterName = decoded.getClaim("name").asString();
            String ownerHash = decoded.getClaim("owner").asString();

            List<String> scopes = new ArrayList<>();
            Object scopeClaim = decoded.getClaim("scp").as(Object.class);
            if (scopeClaim instanceof List<?> list) {
                for (Object o : list) {
                    scopes.add(String.valueOf(o));
                }
            } else if (scopeClaim instanceof String s) {
                scopes.add(s);
            }

            Instant expiresAt = decoded.getExpiresAtAsInstant();
            return new CharacterIdentity(characterId, characterName, scopes, expiresAt, ownerHash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to validate EVE SSO access token", e);
        }
    }

    private static long parseCharacterId(String subject) {
        if (subject == null) {
            throw new IllegalStateException("JWT subject claim missing");
        }
        String[] parts = subject.split(":");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
