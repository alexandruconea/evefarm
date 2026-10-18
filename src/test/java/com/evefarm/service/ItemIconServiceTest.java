package com.evefarm.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ItemIconServiceTest {

    @TempDir
    Path folder;

    @Test
    void aCachedIconReplacesTheOldOneAndLeavesNoPartialFile() throws IOException {
        Path icon = folder.resolve("icon-cache").resolve("34.png");
        ItemIconService.writeCache(icon, new byte[]{1, 2, 3});

        ItemIconService.writeCache(icon, new byte[]{4, 5});

        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(icon));
        try (var files = Files.list(icon.getParent())) {
            assertEquals(List.of(icon), files.toList());
        }
    }

    @Test
    void withoutAConnectionIconsAreNotFetchedOverAndOver() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        List<LogRecord> warnings = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord logRecord) {
                warnings.add(logRecord);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger log = Logger.getLogger(ItemIconService.class.getName());
        log.addHandler(handler);
        try {
            ItemIconService icons = new ItemIconService();
            assertNull(icons.download("http://127.0.0.1:" + closedPort + "/types/34/icon"));
            assertNull(icons.download("http://127.0.0.1:" + closedPort + "/types/35/icon"));
        } finally {
            log.removeHandler(handler);
        }

        assertEquals(1, warnings.size(), "one warning, then a pause");
    }
}
