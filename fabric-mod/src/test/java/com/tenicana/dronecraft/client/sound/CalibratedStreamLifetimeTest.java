package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CalibratedStreamLifetimeTest {
    @Test void stoppingBeforeAttachmentClosesEveryCreatedStreamSynchronouslyOnce() {
        var lifetime=new CalibratedStreamLifetime(new Object());var closed=new AtomicInteger();
        lifetime.register(new Object(),closed::incrementAndGet);lifetime.register(new Object(),closed::incrementAndGet);
        lifetime.retire();assertEquals(2,closed.get());assertFalse(lifetime.runIfActive(()->fail("retired playback")));
        lifetime.retire();assertEquals(2,closed.get());
    }
    @Test void creationAfterStopClosesImmediatelyWithoutPlayback() {
        var lifetime=new CalibratedStreamLifetime(new Object());lifetime.retire();var closed=new AtomicInteger();
        lifetime.register(new Object(),closed::incrementAndGet);assertEquals(1,closed.get());assertFalse(lifetime.active());
    }
    @Test void ordinaryChannelClosureRemovesTheStreamFromLaterRetirement() {
        var lifetime=new CalibratedStreamLifetime(new Object());var key=new Object();var closed=new AtomicInteger();
        lifetime.register(key,closed::incrementAndGet);lifetime.remove(key);lifetime.retire();assertEquals(0,closed.get());
    }
    @Test void playbackAndRetirementShareTheSameMonitor() throws Exception {
        var lifetime=new CalibratedStreamLifetime(new Object());var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> playing=pool.submit(()->lifetime.runIfActive(()-> {
                entered.countDown();try {assertTrue(release.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}return true;
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));Future<?> retired=pool.submit(lifetime::retire);
            assertThrows(TimeoutException.class,()->retired.get(50,TimeUnit.MILLISECONDS));
            release.countDown();assertTrue(playing.get(5,TimeUnit.SECONDS));retired.get(5,TimeUnit.SECONDS);
            assertFalse(lifetime.runIfActive(()->true));
        } finally {release.countDown();pool.shutdownNow();lifetime.retire();}
    }
    @Test void stopAllRetiresRegisteredAndNotYetRegisteredOldInstances() {
        var held=new CalibratedStreamLifetime(new Object());var empty=new CalibratedStreamLifetime(new Object());var closed=new AtomicInteger();
        held.register(new Object(),closed::incrementAndGet);CalibratedStreamLifetime.retireAll();assertEquals(1,closed.get());
        empty.register(new Object(),closed::incrementAndGet);assertEquals(2,closed.get());assertFalse(empty.active());
        var fresh=new CalibratedStreamLifetime(new Object());assertTrue(fresh.runIfActive(()->true));fresh.retire();
    }
    @Test void oneClosureFailureDoesNotLeaveAnotherPendingStreamLive() {
        var lifetime=new CalibratedStreamLifetime(new Object());var closed=new AtomicInteger();
        lifetime.register(new Object(),()-> {throw new IllegalStateException("close failed");});
        lifetime.register(new Object(),closed::incrementAndGet);assertThrows(IllegalStateException.class,lifetime::retire);
        assertEquals(1,closed.get());assertFalse(lifetime.active());lifetime.retire();
    }
    @Test void stopAllContinuesClosingOtherInstancesAfterOneClosureFailure() {
        var failing=new CalibratedStreamLifetime(new Object());var other=new CalibratedStreamLifetime(new Object());
        var closed=new AtomicInteger();failing.register(new Object(),()-> {throw new IllegalStateException("close failed");});
        other.register(new Object(),closed::incrementAndGet);
        assertThrows(IllegalStateException.class,CalibratedStreamLifetime::retireAll);
        assertEquals(1,closed.get());assertFalse(failing.active());assertFalse(other.active());
    }
}
