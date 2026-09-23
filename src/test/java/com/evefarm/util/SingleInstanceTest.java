package com.evefarm.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleInstanceTest {

    @Test
    void aSecondLaunchIsRefusedAndAsksTheFirstToShowItself(@TempDir Path directory) throws Exception {
        Optional<SingleInstance> first = SingleInstance.acquire(directory);
        assertTrue(first.isPresent());
        try (SingleInstance running = first.get()) {
            CountDownLatch shown = new CountDownLatch(1);
            running.onShowRequested(shown::countDown);

            assertFalse(SingleInstance.acquire(directory).isPresent());
            assertTrue(SingleInstance.signalRunningInstance(directory));
            assertTrue(shown.await(5, TimeUnit.SECONDS), "the running instance was asked to show its window");
        }

        Optional<SingleInstance> afterExit = SingleInstance.acquire(directory);
        assertTrue(afterExit.isPresent(), "once the first one exits, a new launch starts normally");
        afterExit.get().close();
    }

    @Test
    void signallingWithNothingRunningFails(@TempDir Path directory) {
        assertFalse(SingleInstance.signalRunningInstance(directory));
    }
}
