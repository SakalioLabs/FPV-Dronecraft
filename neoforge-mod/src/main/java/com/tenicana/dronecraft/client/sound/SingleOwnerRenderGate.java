package com.tenicana.dronecraft.client.sound;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

/** One nonwaiting render attempt; serialized lifecycle callers wait off rendering. */
final class SingleOwnerRenderGate {
    // Bit 0: one render in flight. Bit 1: lifecycle has closed admission.
    private final AtomicInteger state = new AtomicInteger();

    boolean tryEnter() { return state.compareAndSet(0, 1); }

    void leave() {
        if (!state.compareAndSet(1, 0) && !state.compareAndSet(3, 2))
            throw new IllegalStateException("render gate has no active owner");
    }

    // These methods require the producer's lifecycle monitor; never call in rendering.
    void closeForControl() {
        state.getAndUpdate(value -> value | 2);
        while (state.get() != 2) LockSupport.parkNanos(100_000L);
    }

    void openForControl() {
        if (!state.compareAndSet(2, 0)) throw new IllegalStateException("render gate is not quiescent");
    }
}
