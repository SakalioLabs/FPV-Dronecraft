package com.tenicana.dronecraft.client.sound;

import org.lwjgl.system.Callback;
import org.lwjgl.system.CallbackI;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.libffi.FFICIF;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.system.APIUtil.apiClosureRet;
import static org.lwjgl.system.APIUtil.apiCreateCIF;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.MemoryUtil.memGetInt;
import static org.lwjgl.system.libffi.LibFFI.FFI_DEFAULT_ABI;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;

/** ALsizei ABI; the pinned LWJGL 3.3.3 generated adapter returns a pointer. */
final class Signed32PcmCallback extends Callback implements CallbackI {
    private static final FFICIF CIF = apiCreateCIF(FFI_DEFAULT_ABI, ffi_type_sint32,
            ffi_type_pointer, ffi_type_pointer, ffi_type_sint32);
    private final CallbackPcmStream stream;
    private final long token;
    // Bit 0 is one Java callback body; bit 1 closes admission permanently.
    private final AtomicInteger gate = new AtomicInteger();
    private volatile long calls;
    private volatile long requestedBytes, returnedBytes;
    private volatile int fault;

    Signed32PcmCallback(CallbackPcmStream stream) {
        super(CIF);
        this.stream = stream;
        token = address();
    }

    @Override public FFICIF getCallInterface() { return CIF; }

    @Override public void callback(long ret, long args) {
        if (!gate.compareAndSet(0, 1)) {
            // Closed callbacks return EOF. Concurrent native invocation is an
            // ownership failure, never a reason to wait on the mixer thread.
            if ((gate.get() & 2) == 0) fault = 5;
            apiClosureRet(ret, 0);
            return;
        }
        try {
            long user = memGetAddress(memGetAddress(args));
            long samples = memGetAddress(memGetAddress(args + Pointer.POINTER_SIZE));
            int bytes = memGetInt(memGetAddress(args + 2L * Pointer.POINTER_SIZE));
            int written = 0;
            if (user != token || samples == 0) fault = 1;
            else if (bytes <= 0 || (bytes & 1) != 0 || bytes / Short.BYTES > CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES) fault = 2;
            else {
                calls++;
                requestedBytes += bytes;
                try {
                    written = stream.readCallback(samples, bytes);
                    if (written < 0 || written > bytes || (written & 1) != 0) { fault = 3; written = 0; }
                } catch (Throwable ignored) {
                    // Exceptions must not escape a C callback. Valid rendering
                    // paths are separately required to allocate zero bytes.
                    fault = 4;
                    written = 0;
                }
                returnedBytes += written;
            }
            apiClosureRet(ret, written);
        } finally {
            if (!gate.compareAndSet(1, 0)) gate.compareAndSet(3, 2);
        }
    }

    void closeAdmission() { gate.getAndUpdate(value -> value | 2); }

    /** Java-body acknowledgement only; native detach must separately bar entry. */
    void awaitJavaQuiescence() {
        while (gate.get() != 2) LockSupport.parkNanos(100_000L);
    }

    long token() { return token; }
    long calls() { return calls; }
    long requestedBytes() { return requestedBytes; }
    long returnedBytes() { return returnedBytes; }
    int fault() { return fault; }
    int returnType() { return CIF.rtype().type(); }
    long returnSize() { return CIF.rtype().size(); }
}
