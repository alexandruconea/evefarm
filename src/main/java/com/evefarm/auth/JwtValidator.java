package com.evefarm.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.IncorrectClaimException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.evefarm.util.AppInfo;

import java.net.MalformedURLException;
import java.net.URI;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class JwtValidator {

    private static final String JWKS_URL = "https://login.eveonline.com/oauth/jwks";
    private static final String[] ACCEPTED_ISSUERS = {
            "https://login.eveonline.com", "https://login.eveonline.com/", "login.eveonline.com"};
    private static final String AUDIENCE_LITERAL = "EVE Online";
    private static final long CLOCK_SKEW_SECONDS = 60;
    private static final int JWKS_TIMEOUT_MILLIS = 15_000;

    private final JwkProvider jwkProvider;

    public JwtValidator() {
        this(defaultJwkProvider());
    }

    JwtValidator(JwkProvider jwkProvider) {
        this.jwkProvider = jwkProvider;
    }

    private static JwkProvider defaultJwkProvider() {
        try {
            return new UrlJwkProvider(URI.create(JWKS_URL).toURL(), JWKS_TIMEOUT_MILLIS, JWKS_TIMEOUT_MILLIS, null,
                    Map.of("User-Agent", AppInfo.userAgent()));
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid JWKS URL", e);
        }
    }

    public CharacterIdentity validate(String accessTokenJwt, String expectedClientId) {
        try {
            DecodedJWT unverified = JWT.decode(accessTokenJwt);
            Jwk jwk = jwkProvider.get(unverified.getKeyId());

            DecodedJWT decoded = JWT.require(algorithmFor(jwk.getPublicKey()))
                    .withIssuer(ACCEPTED_ISSUERS)
                    .acceptLeeway(CLOCK_SKEW_SECONDS)
                    .ignoreIssuedAt()
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

            return new CharacterIdentity(characterId, characterName, scopes, ownerHash);
        } catch (Exception e) {
            throw new IllegalStateException(describeFailure(e), e);
        }
    }

    static Algorithm algorithmFor(PublicKey key) {
        if (key instanceof RSAPublicKey rsa) {
            return Algorithm.RSA256(rsa, null);
        }
        if (key instanceof ECPublicKey ec) {
            return Algorithm.ECDSA256(ec, null);
        }
        throw new IllegalStateException("Unsupported EVE SSO signing key type: " + key.getAlgorithm());
    }

    static String describeFailure(Exception e) {
        String reason = e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName() : e.getMessage();
        boolean clockRelated = e instanceof TokenExpiredException
                || e instanceof IncorrectClaimException claim
                && ("nbf".equals(claim.getClaimName()) || "iat".equals(claim.getClaimName()));
        if (clockRelated) {
            reason += " Check that this computer's date and time are set automatically.";
        }
        return "Failed to validate EVE SSO access token: " + reason;
    }

    private static long parseCharacterId(String subject) {
        if (subject == null) {
            throw new IllegalStateException("JWT subject claim missing");
        }
        String[] parts = subject.split(":");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
