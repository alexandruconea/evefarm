package com.evefarm.update;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;

public final class ReleaseSignature {

    public static final String RELEASE_PUBLIC_KEY = "MCowBQYDK2VwAyEAUs4cNfVRvJ0utHTeRzWgTCM9R23J7WyStb/L2YVa0Wg=";

    private static final String ALGORITHM = "Ed25519";

    public static String sha256Hex(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    static byte[] message(String version, String fileName, String sha256Hex) {
        return ("EVEFarm release\nversion=" + version + "\nfile=" + fileName + "\nsha256=" + sha256Hex + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    public static String sign(String privateKeyBase64, String version, Path file) throws IOException {
        try {
            PrivateKey key = KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64.trim())));
            Signature signer = Signature.getInstance(ALGORITHM);
            signer.initSign(key);
            signer.update(message(version, file.getFileName().toString(), sha256Hex(file)));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Couldn't sign " + file.getFileName() + " - is the signing key valid?", e);
        }
    }

    public static boolean verify(String publicKeyBase64, String version, String fileName, String sha256Hex,
                                 String signatureBase64) {
        try {
            PublicKey key = KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64.trim())));
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(key);
            verifier.update(message(version, fileName, sha256Hex));
            return verifier.verify(Base64.getDecoder().decode(signatureBase64.trim()));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private ReleaseSignature() {
    }
}
