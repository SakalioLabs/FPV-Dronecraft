package com.tenicana.dronecraft.client.sound;

import java.util.Collection;
import java.util.Objects;
import java.util.OptionalDouble;

/** Conservative dry-asset input budget; not a bound on resampling or other audio. */
final class AssetCatalogHeadroom {
    static final double PEAK_LIMIT = 0.95;

    private AssetCatalogHeadroom() { }

    record Budget(OptionalDouble fullScalePressurePa, double peakBound) { }

    static Budget validate(Collection<VerifiedAssetPcm> assets, int voices) {
        Objects.requireNonNull(assets, "assets");
        if (voices < 1) throw new IllegalArgumentException("asset voice count must be positive");
        OptionalDouble commonScale = OptionalDouble.empty();
        long maximumQuantizedSample = 0;
        for (VerifiedAssetPcm asset : assets) {
            Objects.requireNonNull(asset, "asset");
            double scale = asset.calibration().fullScalePressurePa();
            if (commonScale.isPresent() && Double.compare(commonScale.getAsDouble(), scale) != 0)
                throw new IllegalArgumentException("calibrated asset catalog mixes full-scale pressure references");
            commonScale = OptionalDouble.of(scale);
            maximumQuantizedSample = Math.max(maximumQuantizedSample, asset.maximumQuantizedAbsoluteSample());
        }
        // Count all admitted voices against the largest asset, including across
        // profiles and catalog generations. Phase cancellation is not assumed.
        double bound = maximumQuantizedSample * (double) voices / 32768.0;
        if (!Double.isFinite(bound) || !(bound < PEAK_LIMIT))
            throw new IllegalArgumentException("calibrated asset dry-input peak budget must be below " + PEAK_LIMIT);
        return new Budget(commonScale, bound);
    }
}
