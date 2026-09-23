package com.evefarm.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BackgroundLoader {

    private static final Logger LOG = Logger.getLogger(BackgroundLoader.class.getName());

    static <T> void load(AtomicInteger generation, Supplier<T> query, Consumer<T> apply, String what,
                         Runnable onFailure) {
        int current = generation.incrementAndGet();
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() {
                return query.get();
            }

            @Override
            protected void done() {
                if (current != generation.get()) {
                    return;
                }
                try {
                    apply.accept(get());
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "Failed to load " + what, e);
                    onFailure.run();
                }
            }
        }.execute();
    }

    private BackgroundLoader() {
    }
}
