package com.tenicana.dronecraft.client.sound;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;

/** Publishes a catalog and the session's pressure reference as one immutable value. */
final class AssetCatalogPublication<T> {
    record Snapshot<T>(T catalog, OptionalDouble fullScalePressurePa) {
        Snapshot {
            Objects.requireNonNull(catalog, "catalog");
            requireScale(fullScalePressurePa);
        }
    }

    private final AtomicReference<Snapshot<T>> current;

    AssetCatalogPublication(T initial, OptionalDouble initialFullScalePressurePa) {
        current = new AtomicReference<>(new Snapshot<>(initial, initialFullScalePressurePa));
    }

    Snapshot<T> snapshot() { return current.get(); }

    Snapshot<T> publish(T candidate, OptionalDouble candidateFullScalePressurePa) {
        Objects.requireNonNull(candidate, "candidate");
        requireScale(candidateFullScalePressurePa);
        while (true) {
            Snapshot<T> previous = current.get();
            OptionalDouble stickyScale = previous.fullScalePressurePa();
            if (stickyScale.isPresent() && candidateFullScalePressurePa.isPresent()
                    && Double.compare(stickyScale.getAsDouble(), candidateFullScalePressurePa.getAsDouble()) != 0)
                throw new IllegalArgumentException("calibrated asset publication changes the session full-scale pressure reference");
            OptionalDouble nextScale = stickyScale.isPresent() ? stickyScale : candidateFullScalePressurePa;
            Snapshot<T> next = new Snapshot<>(candidate, nextScale);
            if (current.compareAndSet(previous, next)) return next;
        }
    }

    private static void requireScale(OptionalDouble scale) {
        Objects.requireNonNull(scale, "fullScalePressurePa");
        if (scale.isPresent() && (!Double.isFinite(scale.getAsDouble()) || scale.getAsDouble() <= 0))
            throw new IllegalArgumentException("full-scale pressure reference must be finite and positive");
    }
}
