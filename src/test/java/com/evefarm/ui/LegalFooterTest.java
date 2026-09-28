package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LegalFooterTest {

    private static final String DEVELOPER_LICENSE_NOTICE = "© 2014 CCP hf. All rights reserved. \"EVE\", "
            + "\"EVE Online\", \"CCP\", and all related logos and images are trademarks or registered trademarks of "
            + "CCP hf.";

    @Test
    void theAppShowsTheNoticeTheDeveloperLicenseAsksFor() {
        assertTrue(LegalFooter.FULL_NOTICE.contains(DEVELOPER_LICENSE_NOTICE));
        assertTrue(LegalFooter.SHORT_NOTICE.contains("Not affiliated with Fenris Creations"));
    }

    @Test
    void theNoticeInTheAppMatchesTheReadme() throws IOException {
        String readme = Files.readString(Path.of("README.md"), StandardCharsets.UTF_8).replace("\r\n", "\n")
                .replace("[MIT License](LICENSE)", "MIT License")
                .replace(" (see Trademarks below)", "");
        for (String paragraph : LegalFooter.FULL_NOTICE.split("\n\n")) {
            assertTrue(readme.contains(paragraph), "README is missing: " + paragraph);
        }
    }
}
