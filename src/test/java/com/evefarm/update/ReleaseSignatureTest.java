package com.evefarm.update;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseSignatureTest {

    private static String privateKey;
    private static String publicKey;

    @BeforeAll
    static void createTestKeys() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }

    @Test
    void aSignedReleaseVerifies(@TempDir Path dir) throws Exception {
        Path zip = Files.writeString(dir.resolve("EVEFarm-1.4.0-win64.zip"), "release bytes");
        String signature = ReleaseSignature.sign(privateKey, "1.4.0", zip);

        assertTrue(ReleaseSignature.verify(publicKey, "1.4.0", zip.getFileName().toString(),
                ReleaseSignature.sha256Hex(zip), signature));
    }

    @Test
    void aTamperedFileAnotherVersionOrAnotherKeyIsRejected(@TempDir Path dir) throws Exception {
        Path zip = Files.writeString(dir.resolve("EVEFarm-1.4.0-win64.zip"), "release bytes");
        String signature = ReleaseSignature.sign(privateKey, "1.4.0", zip);
        String name = zip.getFileName().toString();
        String hash = ReleaseSignature.sha256Hex(zip);

        Files.writeString(zip, "release bytes, modified");
        assertFalse(ReleaseSignature.verify(publicKey, "1.4.0", name, ReleaseSignature.sha256Hex(zip), signature));
        assertFalse(ReleaseSignature.verify(publicKey, "1.5.0", name, hash, signature),
                "an old signed release can't be passed off as a newer version");
        assertFalse(ReleaseSignature.verify(ReleaseSignature.RELEASE_PUBLIC_KEY, "1.4.0", name, hash, signature),
                "a signature made with any other key fails against the key the app ships with");
        assertFalse(ReleaseSignature.verify(publicKey, "1.4.0", name, hash, "not base64 at all!"));
    }

    @Test
    void sha256MatchesAKnownValue(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("abc.txt"), "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ReleaseSignature.sha256Hex(file));
    }
}
