package com.evefarm.auth;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.SigningKeyNotFoundException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtValidatorTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final long CHARACTER_ID = 90000001L;

    private static KeyPair rsaKeys;
    private static KeyPair ecKeys;
    private static JwtValidator validator;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        rsaKeys = rsa.generateKeyPair();
        KeyPairGenerator ec = KeyPairGenerator.getInstance("EC");
        ec.initialize(new ECGenParameterSpec("secp256r1"));
        ecKeys = ec.generateKeyPair();

        Map<String, Jwk> keys = Map.of(
                "rsa-key", Jwk.fromValues(rsaJwk("rsa-key", (RSAPublicKey) rsaKeys.getPublic())),
                "ec-key", Jwk.fromValues(ecJwk("ec-key", (ECPublicKey) ecKeys.getPublic())));
        validator = new JwtValidator(keyId -> {
            Jwk jwk = keys.get(keyId);
            if (jwk == null) {
                throw new SigningKeyNotFoundException("No key " + keyId, null);
            }
            return jwk;
        });
    }

    @Test
    void acceptsATokenSignedWithTheRsaKey() {
        String token = token("rsa-key", "https://login.eveonline.com", Instant.now()).sign(rsa());

        CharacterIdentity identity = validator.validate(token, CLIENT_ID);

        assertEquals(CHARACTER_ID, identity.characterId());
        assertEquals("Test Pilot", identity.characterName());
        assertEquals("owner-hash", identity.ownerHash());
        assertEquals(List.of("esi-wallet.read_character_wallet.v1"), identity.scopes());
    }

    @Test
    void acceptsATokenSignedWithTheEcKey() {
        String token = token("ec-key", "https://login.eveonline.com", Instant.now()).sign(ec());

        assertEquals(CHARACTER_ID, validator.validate(token, CLIENT_ID).characterId());
    }

    @Test
    void acceptsEveryIssuerSpellingTheSsoUses() {
        for (String issuer : List.of("https://login.eveonline.com", "https://login.eveonline.com/", "login.eveonline.com")) {
            String token = token("rsa-key", issuer, Instant.now()).sign(rsa());

            assertEquals(CHARACTER_ID, validator.validate(token, CLIENT_ID).characterId(), issuer);
        }
    }

    @Test
    void toleratesAComputerClockThatIsBehindTheSsoServer() {
        String token = token("rsa-key", "https://login.eveonline.com", Instant.now().plusSeconds(30)).sign(rsa());

        assertEquals(CHARACTER_ID, validator.validate(token, CLIENT_ID).characterId());
    }

    @Test
    void rejectsATokenIssuedForAnotherApplication() {
        String token = token("rsa-key", "https://login.eveonline.com", Instant.now()).sign(rsa());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> validator.validate(token, "another-client-id"));

        assertTrue(error.getMessage().contains("audience"), error.getMessage());
    }

    @Test
    void rejectsATokenFromAnotherIssuer() {
        String token = token("rsa-key", "https://login.example.com", Instant.now()).sign(rsa());

        assertThrows(IllegalStateException.class, () -> validator.validate(token, CLIENT_ID));
    }

    @Test
    void rejectsATokenWhoseAlgorithmDoesNotMatchTheKey() {
        String token = token("rsa-key", "https://login.eveonline.com", Instant.now())
                .sign(Algorithm.HMAC256("guessed-secret"));

        assertThrows(IllegalStateException.class, () -> validator.validate(token, CLIENT_ID));
    }

    @Test
    void rejectsATokenSignedByAnotherKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair other = generator.generateKeyPair();
        String token = token("rsa-key", "https://login.eveonline.com", Instant.now())
                .sign(Algorithm.RSA256((RSAPublicKey) other.getPublic(), (RSAPrivateKey) other.getPrivate()));

        assertThrows(IllegalStateException.class, () -> validator.validate(token, CLIENT_ID));
    }

    @Test
    void anExpiredTokenPointsAtTheComputerClock() {
        Instant issued = Instant.now().minusSeconds(3600);
        String token = token("rsa-key", "https://login.eveonline.com", issued)
                .withExpiresAt(issued.plusSeconds(1200))
                .sign(rsa());

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> validator.validate(token, CLIENT_ID));

        assertTrue(error.getMessage().contains("date and time"), error.getMessage());
    }

    private static JWTCreator.Builder token(String keyId, String issuer, Instant issuedAt) {
        return JWT.create()
                .withKeyId(keyId)
                .withIssuer(issuer)
                .withAudience(CLIENT_ID, "EVE Online")
                .withSubject("CHARACTER:EVE:" + CHARACTER_ID)
                .withClaim("name", "Test Pilot")
                .withClaim("owner", "owner-hash")
                .withClaim("scp", List.of("esi-wallet.read_character_wallet.v1"))
                .withIssuedAt(issuedAt)
                .withExpiresAt(issuedAt.plusSeconds(1200));
    }

    private static Algorithm rsa() {
        return Algorithm.RSA256((RSAPublicKey) rsaKeys.getPublic(), (RSAPrivateKey) rsaKeys.getPrivate());
    }

    private static Algorithm ec() {
        return Algorithm.ECDSA256((ECPublicKey) ecKeys.getPublic(), (ECPrivateKey) ecKeys.getPrivate());
    }

    private static Map<String, Object> rsaJwk(String keyId, RSAPublicKey key) {
        return Map.of("kid", keyId, "kty", "RSA", "alg", "RS256", "use", "sig",
                "n", base64Url(unsigned(key.getModulus())),
                "e", base64Url(unsigned(key.getPublicExponent())));
    }

    private static Map<String, Object> ecJwk(String keyId, ECPublicKey key) {
        return Map.of("kid", keyId, "kty", "EC", "alg", "ES256", "use", "sig", "crv", "P-256",
                "x", base64Url(fixedLength(key.getW().getAffineX(), 32)),
                "y", base64Url(fixedLength(key.getW().getAffineY(), 32)));
    }

    private static byte[] unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static byte[] fixedLength(BigInteger value, int length) {
        byte[] bytes = unsigned(value);
        byte[] padded = new byte[length];
        System.arraycopy(bytes, 0, padded, length - bytes.length, bytes.length);
        return padded;
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
