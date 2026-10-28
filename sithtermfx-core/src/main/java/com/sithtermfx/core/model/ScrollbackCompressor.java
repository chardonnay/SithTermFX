package com.sithtermfx.core.model;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The background side of the scrollback compression: one low-priority daemon thread that, every
 * {@value #TICK_MILLIS} ms, gives each terminal with compression turned on a slice of work
 * ({@link TerminalTextBuffer#compressIdleHistory}). A terminal is only worked on once it has had no
 * output for {@link #IDLE_NANOS}, and only if its lock is free, so compression never competes with
 * output or rendering. Terminals are held weakly; the thread ends when none is left.
 */
final class ScrollbackCompressor {

    private static final Logger logger = LoggerFactory.getLogger(ScrollbackCompressor.class);

    static final long TICK_MILLIS = 500;

    /** How long a terminal must have had no output before its history is compressed. */
    static final long IDLE_NANOS = TimeUnit.MILLISECONDS.toNanos(1500);

    /** How long one terminal's slice may hold its lock. */
    static final long SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(4);

    private static final List<WeakReference<TerminalTextBuffer>> ourBuffers = new ArrayList<>();

    private static ScheduledExecutorService ourExecutor;

    private ScrollbackCompressor() {
    }

    static synchronized void register(@NotNull TerminalTextBuffer buffer) {
        for (WeakReference<TerminalTextBuffer> ref : ourBuffers) {
            if (ref.get() == buffer) {
                return;
            }
        }
        ourBuffers.add(new WeakReference<>(buffer));
        if (ourExecutor == null) {
            ourExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "SithTermFX scrollback compression");
                thread.setDaemon(true);
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
            ourExecutor.scheduleWithFixedDelay(ScrollbackCompressor::tick, TICK_MILLIS, TICK_MILLIS,
                    TimeUnit.MILLISECONDS);
        }
    }

    static synchronized void unregister(@NotNull TerminalTextBuffer buffer) {
        ourBuffers.removeIf(ref -> {
            TerminalTextBuffer registered = ref.get();
            return registered == null || registered == buffer;
        });
        stopIfUnused();
    }

    /** Whether the compression thread is running; for tests. */
    static synchronized boolean isRunning() {
        return ourExecutor != null;
    }

    private static void tick() {
        List<TerminalTextBuffer> buffers = new ArrayList<>();
        synchronized (ScrollbackCompressor.class) {
            ourBuffers.removeIf(ref -> {
                TerminalTextBuffer buffer = ref.get();
                if (buffer == null) {
                    return true;
                }
                buffers.add(buffer);
                return false;
            });
            if (stopIfUnused()) {
                return;
            }
        }
        long now = System.nanoTime();
        for (TerminalTextBuffer buffer : buffers) {
            try {
                buffer.compressIdleHistory(now, IDLE_NANOS, SLICE_NANOS);
            } catch (Throwable t) {
                // A failing terminal must not end the thread for the others (a scheduled task that
                // throws is never run again).
                logger.warn("Scrollback compression failed", t);
            }
        }
    }

    private static boolean stopIfUnused() {
        if (ourBuffers.isEmpty() && ourExecutor != null) {
            ourExecutor.shutdown();
            ourExecutor = null;
            return true;
        }
        return false;
    }
}
