import com.evefarm.update.ReleaseSignature;

import java.nio.file.Files;
import java.nio.file.Path;

public class SignRelease {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: java -cp target/classes packaging/SignRelease.java <zip> <version>");
            System.exit(2);
        }
        String privateKey = System.getenv("RELEASE_SIGNING_KEY");
        if (privateKey == null || privateKey.isBlank()) {
            System.err.println("RELEASE_SIGNING_KEY is not set - add it as a repository secret on GitHub.");
            System.exit(1);
        }
        Path zip = Path.of(args[0]);
        String version = args[1];
        String signature = ReleaseSignature.sign(privateKey, version, zip);
        String sha256 = ReleaseSignature.sha256Hex(zip);
        if (!ReleaseSignature.verify(ReleaseSignature.RELEASE_PUBLIC_KEY, version, zip.getFileName().toString(),
                sha256, signature)) {
            System.err.println("RELEASE_SIGNING_KEY doesn't match the public key built into the app - "
                    + "installed copies would reject this release, so it isn't published.");
            System.exit(1);
        }
        Files.writeString(Path.of(zip + ".sig"), signature + "\n");
        Files.writeString(Path.of(zip + ".sha256"), sha256 + "  " + zip.getFileName() + "\n");
        System.out.println("Signed " + zip.getFileName() + " (sha256 " + sha256 + ")");
    }
}
