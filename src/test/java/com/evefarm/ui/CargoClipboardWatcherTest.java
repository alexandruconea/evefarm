package com.evefarm.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CargoClipboardWatcherTest {

    private final AtomicReference<String> clipboard = new AtomicReference<>();
    private final AtomicBoolean appActive = new AtomicBoolean();
    private final List<String> copies = new ArrayList<>();
    private final CargoClipboardWatcher watcher = new CargoClipboardWatcher(clipboard::get, appActive::get, copies::add);

    @Test
    void whatIsInTheClipboardWhenTrackingStartsIsOfferedOnce() {
        clipboard.set("Tritanium\t10");

        watcher.start();
        watcher.poll();
        watcher.stop();

        assertEquals(List.of("Tritanium\t10"), copies);
    }

    @Test
    void eachNewCopyMadeInAnotherAppIsOffered() {
        watcher.start();
        clipboard.set("Tritanium\t10");
        watcher.poll();
        watcher.poll();
        clipboard.set("Tritanium\t25");
        watcher.poll();
        watcher.stop();

        assertEquals(List.of("Tritanium\t10", "Tritanium\t25"), copies);
    }

    @Test
    void copiesMadeInsideEveFarmAreIgnored() {
        watcher.start();
        appActive.set(true);
        clipboard.set("Malpais Legate\tTritanium");
        watcher.poll();
        appActive.set(false);
        watcher.poll();
        watcher.stop();

        assertEquals(List.of(), copies);
    }

    @Test
    void textPastedByHandIsNotOfferedAgain() {
        watcher.start();
        clipboard.set("Tritanium\t10");
        watcher.markSeen("Tritanium\t10");
        watcher.poll();
        watcher.stop();

        assertEquals(List.of(), copies);
    }
}
