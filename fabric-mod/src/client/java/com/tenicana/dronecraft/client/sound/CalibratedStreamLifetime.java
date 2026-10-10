package com.tenicana.dronecraft.client.sound;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/** CPU stream ownership from creation through attachment, playback and retirement. */
final class CalibratedStreamLifetime {
    private static final Set<CalibratedStreamLifetime> REGISTERED = ConcurrentHashMap.newKeySet();
    private static final AtomicLong EPOCH = new AtomicLong();
    private final Object monitor;
    private final long epoch = EPOCH.get();
    private final IdentityHashMap<Object, Runnable> streams = new IdentityHashMap<>();
    private boolean active = true;

    CalibratedStreamLifetime(Object monitor) { this.monitor = monitor; }
    Object monitor() { return monitor; }

    boolean active() {
        synchronized (monitor) { return active && epoch == EPOCH.get(); }
    }

    void register(Object stream, Runnable close) {
        synchronized (monitor) {
            if (!active || epoch != EPOCH.get()) { close.run(); return; }
            if (streams.putIfAbsent(stream, close) != null) throw new IllegalStateException("duplicate calibrated stream");
            REGISTERED.add(this);
            // A stopAll snapshot may have run between the first epoch check and registration.
            if (epoch != EPOCH.get()) retire();
        }
    }

    void remove(Object stream) {
        synchronized (monitor) {
            streams.remove(stream);
            if (streams.isEmpty()) REGISTERED.remove(this);
        }
    }

    boolean runIfActive(BooleanSupplier action) {
        synchronized (monitor) { return active && epoch == EPOCH.get() && action.getAsBoolean(); }
    }

    void retire() {
        synchronized (monitor) {
            active = false;
            var close = new ArrayList<>(streams.values());
            streams.clear();
            REGISTERED.remove(this);
            RuntimeException failure = null;
            for (Runnable action : close) {
                try { action.run(); }
                catch (RuntimeException error) {
                    if (failure == null) failure = error; else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }

    static void retireAll() {
        EPOCH.incrementAndGet();
        // No global lock is held while entering an instance/state monitor or closing PCM.
        RuntimeException failure = null;
        for (var lifetime : new ArrayList<>(REGISTERED)) {
            try { lifetime.retire(); }
            catch (RuntimeException error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        if (failure != null) throw failure;
    }
}
