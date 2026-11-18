package com.evefarm.ui;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

final class BackgroundLoader {

    private static final Logger LOG = Logger.getLogger(BackgroundLoader.class.getName());

    static <T> void load(Supplier<T> query, Consumer<T> apply, String what, Consumer<Throwable> onFailure) {
        load(0, new AtomicInteger(), query, apply, what, onFailure);
    }

    static <T> void load(AtomicInteger generation, Supplier<T> query, Consumer<T> apply, String what) {
        load(generation.incrementAndGet(), generation, query, apply, what, failure -> {
        });
    }

    static <T> void load(AtomicInteger generation, Supplier<T> query, Consumer<T> apply, String what,
                         Consumer<Throwable> onFailure) {
        load(generation.incrementAndGet(), generation, query, apply, what, onFailure);
    }

    static <T> void load(int current, AtomicInteger generation, Supplier<T> query, Consumer<T> apply, String what) {
        load(current, generation, query, apply, what, failure -> {
        });
    }

    static <T> void load(int current, AtomicInteger generation, Supplier<T> query, Consumer<T> apply, String what,
                         Consumer<Throwable> onFailure) {
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
                    Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
                    LOG.log(Level.WARNING, "Failed to load " + what, cause);
                    onFailure.accept(cause);
                }
            }
        }.execute();
    }

    private BackgroundLoader() {
    }
}
