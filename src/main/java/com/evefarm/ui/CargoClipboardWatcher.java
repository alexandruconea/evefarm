package com.evefarm.ui;

import javax.swing.Timer;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class CargoClipboardWatcher {

    private static final int POLL_MILLIS = 1000;
    private static final int MAX_LENGTH = 200_000;

    private final Supplier<String> reader;
    private final BooleanSupplier appIsActive;
    private final Consumer<String> onCopied;
    private final Timer timer;
    private String lastSeen;

    CargoClipboardWatcher(Consumer<String> onCopied) {
        this(CargoClipboardWatcher::readSystemClipboard,
                () -> KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null, onCopied);
    }

    CargoClipboardWatcher(Supplier<String> reader, BooleanSupplier appIsActive, Consumer<String> onCopied) {
        this.reader = reader;
        this.appIsActive = appIsActive;
        this.onCopied = onCopied;
        this.timer = new Timer(POLL_MILLIS, e -> poll());
    }

    void start() {
        lastSeen = reader.get();
        if (lastSeen != null) {
            onCopied.accept(lastSeen);
        }
        timer.start();
    }

    void stop() {
        timer.stop();
    }

    void markSeen(String text) {
        lastSeen = text;
    }

    void poll() {
        String text = reader.get();
        if (text == null || Objects.equals(text, lastSeen)) {
            return;
        }
        lastSeen = text;
        if (!appIsActive.getAsBoolean()) {
            onCopied.accept(text);
        }
    }

    static String readSystemClipboard() {
        try {
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return null;
            }
            String text = (String) clipboard.getData(DataFlavor.stringFlavor);
            return text == null || text.length() > MAX_LENGTH ? null : text;
        } catch (Exception e) {
            return null;
        }
    }
}
