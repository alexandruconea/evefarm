package com.evefarm.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserTest {

    @Test
    void onlyWebLinksAreOpened() {
        assertTrue(Browser.isWebLink(URI.create("https://login.eveonline.com/v2/oauth/authorize?x=1")));
        assertTrue(Browser.isWebLink(URI.create("http://127.0.0.1:8086/callback")));
        assertFalse(Browser.isWebLink(URI.create("file:///etc/passwd")));
        assertFalse(Browser.isWebLink(URI.create("javascript:alert(1)")));
        assertFalse(Browser.isWebLink(URI.create("https:relative")));
        assertThrows(IOException.class, () -> Browser.open("file:///C:/Windows/notepad.exe"));
    }

    @Test
    void xdgOpenGetsTheLinkAsOneArgument() {
        URI link = URI.create("https://zkillboard.com/character/2112625428/?a=1&b=2");

        ProcessBuilder command = Browser.linuxCommand(Path.of("/usr/bin/xdg-open"), link);

        assertEquals(List.of(Path.of("/usr/bin/xdg-open").toString(), link.toString()), command.command());
    }
}
