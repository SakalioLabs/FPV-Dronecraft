package com.tenicana.dronecraft.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetCatalogHeadroomTest {
    @Test
    void twelveVoicesAccept2594AndRejectTheNextQuantizedInteger() {
        VerifiedAssetPcm safe = asset(new short[]{2594, -2594}, 2, 1, 8, 0);
        assertEquals(2594, safe.maximumQuantizedAbsoluteSample());
        var budget = AssetCatalogHeadroom.validate(List.of(safe), 12);
        // Independent integer oracle: twelve worst-phase samples total 31128.
        assertEquals(31128.0 / 32768.0, budget.peakBound());
        assertEquals(8, budget.fullScalePressurePa().orElseThrow());
        assertTrue(budget.peakBound() < 0.95);

        VerifiedAssetPcm unsafe = asset(new short[]{2595, -2595}, 2, 1, 8, 0);
        assertEquals(2595, unsafe.maximumQuantizedAbsoluteSample());
        assertTrue(31140.0 / 32768.0 > 0.95);
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(unsafe), 12));
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(safe), 13));
    }

    @Test
    void everyAdmittedVoiceUsesTheLargestAssetRatherThanAddingCatalogEntries() {
        VerifiedAssetPcm quiet = asset(new short[]{100, -100}, 2, 1, 2, 0);
        VerifiedAssetPcm loud = asset(new short[]{2594, -2594}, 2, 1, 2, 0);
        var budget = AssetCatalogHeadroom.validate(List.of(quiet, loud, quiet, loud), 12);
        assertEquals(31128.0 / 32768.0, budget.peakBound());
        assertEquals(budget, AssetCatalogHeadroom.validate(List.of(loud), 12));
    }

    @Test
    void wholeDecodedLoopOutsideTheReferenceWindowCountsAgainstHeadroom() {
        VerifiedAssetPcm asset = asset(new short[]{1000, -1000, 2595}, 2, 1, 1, 0);
        assertEquals(1000.0 / 32768.0, asset.calibration().referenceDigitalRms());
        assertEquals(2595, asset.maximumQuantizedAbsoluteSample());
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(asset), 12));
    }

    @Test
    void maximumResponseGridGainCoversAnInterpolatedOperatingPoint() {
        VerifiedAssetPcm asset = asset(new short[]{1000, -1000}, 2, 1, 1, 20);
        var calibration = asset.calibration();
        assertEquals(1, calibration.amplitude(12000, 0), 1e-12);
        assertEquals(10, calibration.amplitude(16000, 1), 1e-12);
        assertEquals(Math.sqrt(10), calibration.amplitude(Math.sqrt(12000.0 * 16000.0), .5), 1e-12);
        assertEquals(10000, asset.maximumQuantizedAbsoluteSample());
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(asset), 12));
    }

    @Test
    void acceptedCursorToleranceCrossesANegativeHalfIntegerAtTheBudgetBoundary() {
        VerifiedAssetPcm asset = asset(new short[]{-5189}, 1, .5, 1, 0);
        // At the declared gain -2594.5 rounds toward positive infinity to -2594.
        assertEquals(-2594, asset.cursor(false).read(2, .5).getShort());
        // The same tolerated gain used by Cursor permits the next negative integer.
        double acceptedMaximum = .5 * (1 + 1e-12);
        assertEquals(-2595, asset.cursor(false).read(2, acceptedMaximum).getShort());
        assertEquals(2595, asset.maximumQuantizedAbsoluteSample());
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(asset), 12));
    }

    @Test
    void commonFullScaleReferenceIsExactAndDoesNotForceOnePascal() {
        VerifiedAssetPcm a = asset(new short[]{100, -100}, 2, 1, 8, 0);
        VerifiedAssetPcm b = asset(new short[]{200, -200}, 2, 1, 8, 0);
        assertEquals(8, AssetCatalogHeadroom.validate(List.of(a, b), 12)
                .fullScalePressurePa().orElseThrow());
        VerifiedAssetPcm different = asset(new short[]{100, -100}, 2, 1, Math.nextUp(8.0), 0);
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(a, different), 12));
    }

    @Test
    void emptyCatalogHasNoDeclaredPressureReferenceAndStillRequiresARealVoiceBudget() {
        var budget = AssetCatalogHeadroom.validate(List.of(), 12);
        assertFalse(budget.fullScalePressurePa().isPresent());
        assertEquals(0, budget.peakBound());
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(), 0));
        assertThrows(IllegalArgumentException.class,
                () -> AssetCatalogHeadroom.validate(List.of(), -1));
    }

    @Test
    void verifiedSamplesAndReturnedBudgetRemainImmutableWhenCallerInputsChange() {
        byte[] pcm = littleEndian(new short[]{100, -100});
        var calibration = calibration(pcm, 2, 1, 4, 0);
        VerifiedAssetPcm asset = VerifiedAssetPcm.verify(calibration, pcm);
        var collection = new ArrayList<>(List.of(asset));
        var budget = AssetCatalogHeadroom.validate(collection, 12);
        pcm[0] = 0;
        pcm[1] = 0;
        collection.clear();
        assertEquals(100, asset.cursor(false).read(2, 1).getShort());
        assertEquals(100, asset.maximumQuantizedAbsoluteSample());
        assertEquals(1200.0 / 32768.0, budget.peakBound());
        assertEquals(4, budget.fullScalePressurePa().orElseThrow());
    }

    private static VerifiedAssetPcm asset(short[] samples, int referenceFrames,
                                          double amplitude, double fullScalePa, double maximumGainDb) {
        byte[] pcm = littleEndian(samples);
        return VerifiedAssetPcm.verify(calibration(pcm, referenceFrames, amplitude, fullScalePa, maximumGainDb), pcm);
    }

    private static AudioAssetCalibration calibration(byte[] pcm, int referenceFrames,
                                                      double amplitude, double fullScalePa, double maximumGainDb) {
        var reference = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        double squares = 0;
        for (int i = 0; i < referenceFrames; i++) {
            double sample = reference.get(i) / 32768.0;
            squares += sample * sample;
        }
        double rms = Math.sqrt(squares / referenceFrames);
        return new AudioAssetCalibration("a".repeat(64), VerifiedAssetPcm.sha256(pcm), 48000, pcm.length / 2,
                0, referenceFrames, rms, rms * amplitude * fullScalePa, fullScalePa, 12000, 0,
                List.of(8000.0, 12000.0, 16000.0), List.of(-1.0, 0.0, 1.0),
                List.of(List.of(0.0, 0.0, 0.0), List.of(0.0, 0.0, 0.0),
                        List.of(0.0, maximumGainDb, maximumGainDb)));
    }

    private static byte[] littleEndian(short[] samples) {
        var bytes = ByteBuffer.allocate(samples.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short sample : samples) bytes.putShort(sample);
        return bytes.array();
    }
}
