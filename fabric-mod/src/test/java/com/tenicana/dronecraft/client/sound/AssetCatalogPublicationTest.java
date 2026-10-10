package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.OptionalDouble;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class AssetCatalogPublicationTest {
    @Test
    void firstNonemptyPublicationChoosesItsDeclaredScaleWithoutForcingOnePascal() {
        Object empty = new Object();
        Object next = new Object();
        var publication = new AssetCatalogPublication<>(empty, OptionalDouble.empty());
        var original = publication.snapshot();
        var installed = publication.publish(next, OptionalDouble.of(8));
        assertSame(installed, publication.snapshot());
        assertSame(next, installed.catalog());
        assertEquals(8, installed.fullScalePressurePa().orElseThrow());
        assertSame(empty, original.catalog());
        assertFalse(original.fullScalePressurePa().isPresent());
    }

    @Test
    void matchingScaleReplacesTheCatalogWithoutMutatingAnExistingSnapshot() {
        Object a = new Object();
        Object b = new Object();
        var publication = new AssetCatalogPublication<>(a, OptionalDouble.of(2));
        var original = publication.snapshot();
        publication.publish(b, OptionalDouble.of(2));
        assertSame(b, publication.snapshot().catalog());
        assertEquals(2, publication.snapshot().fullScalePressurePa().orElseThrow());
        assertSame(a, original.catalog());
        assertEquals(2, original.fullScalePressurePa().orElseThrow());
    }

    @Test
    void scaleMismatchIncludingOneUlpPreservesTheExactPreviousHolder() {
        var publication = new AssetCatalogPublication<>(new Object(), OptionalDouble.of(2));
        var before = publication.snapshot();
        assertThrows(IllegalArgumentException.class,
                () -> publication.publish(new Object(), OptionalDouble.of(Math.nextUp(2.0))));
        assertSame(before, publication.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> publication.publish(new Object(), OptionalDouble.of(8)));
        assertSame(before, publication.snapshot());
    }

    @Test
    void emptyPublicationRetainsStickyScaleAndCannotEnableALaterScaleChange() {
        var publication = new AssetCatalogPublication<>(new Object(), OptionalDouble.of(8));
        Object empty = new Object();
        var installed = publication.publish(empty, OptionalDouble.empty());
        assertSame(empty, installed.catalog());
        assertEquals(8, installed.fullScalePressurePa().orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> publication.publish(new Object(), OptionalDouble.of(16)));
        assertSame(installed, publication.snapshot());
        Object compatible = new Object();
        publication.publish(compatible, OptionalDouble.of(8));
        assertSame(compatible, publication.snapshot().catalog());
    }

    @Test
    void invalidCandidatesNeverUpdateEitherPartOfTheCurrentState() {
        var publication = new AssetCatalogPublication<>(new Object(), OptionalDouble.of(4));
        var before = publication.snapshot();
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> publication.publish(new Object(), OptionalDouble.of(invalid)));
            assertSame(before, publication.snapshot());
        }
        assertThrows(NullPointerException.class, () -> publication.publish(null, OptionalDouble.empty()));
        assertThrows(NullPointerException.class, () -> publication.publish(new Object(), null));
        assertSame(before, publication.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> new AssetCatalogPublication<>(new Object(), OptionalDouble.of(0)));
    }

    @Test
    void compatibleDeferredPublicationStaysInvisibleUntilItsExecutorRuns() {
        Object initial = new Object();
        Object candidate = new Object();
        var publication = new AssetCatalogPublication<>(initial, OptionalDouble.of(8));
        Queue<Runnable> queue = new ArrayDeque<>();
        Executor deferred = queue::add;
        var future = CompletableFuture.runAsync(() -> publication.publish(candidate, OptionalDouble.of(8)), deferred);
        assertSame(initial, publication.snapshot().catalog());
        assertFalse(future.isDone());
        queue.remove().run();
        future.join();
        assertSame(candidate, publication.snapshot().catalog());
    }

    @Test
    void deferredPublicationChecksTheScaleAtExecutionAfterAnotherFirstPublication() {
        var publication = new AssetCatalogPublication<>(new Object(), OptionalDouble.empty());
        Queue<Runnable> queue = new ArrayDeque<>();
        Executor deferred = queue::add;
        var delayed = CompletableFuture.runAsync(() -> publication.publish(new Object(), OptionalDouble.of(16)), deferred);
        Object first = new Object();
        var before = publication.publish(first, OptionalDouble.of(8));
        queue.remove().run();
        var error = assertThrows(CompletionException.class, delayed::join);
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertSame(before, publication.snapshot());
        assertSame(first, publication.snapshot().catalog());
        assertEquals(8, publication.snapshot().fullScalePressurePa().orElseThrow());
    }

    @Test
    void competingFirstPublicationsCannotInstallCatalogAndScaleFromDifferentWinners() throws Exception {
        Object a = new Object();
        Object b = new Object();
        var publication = new AssetCatalogPublication<>(new Object(), OptionalDouble.empty());
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> attempt(publication, a, 8, ready, start));
            var second = executor.submit(() -> attempt(publication, b, 16, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            boolean aAccepted = first.get(5, TimeUnit.SECONDS);
            boolean bAccepted = second.get(5, TimeUnit.SECONDS);
            assertTrue(aAccepted ^ bAccepted);
            var installed = publication.snapshot();
            assertSame(aAccepted ? a : b, installed.catalog());
            assertEquals(aAccepted ? 8 : 16, installed.fullScalePressurePa().orElseThrow());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private static boolean attempt(AssetCatalogPublication<Object> publication, Object catalog,
                                   double scale, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("publication start gate timed out");
        try {
            publication.publish(catalog, OptionalDouble.of(scale));
            return true;
        } catch (IllegalArgumentException expected) {
            return false;
        }
    }
}
