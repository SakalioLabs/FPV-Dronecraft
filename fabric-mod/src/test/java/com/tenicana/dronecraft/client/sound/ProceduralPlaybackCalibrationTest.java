package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.*;
import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer.Layer;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic PCM controls only; no measured profile or physical evidence is fabricated. */
final class ProceduralPlaybackCalibrationTest {
    private static final int FRAMES = 48_000, SEED = 157;
    private static final double FOUR_DB = 20.0 * Math.log10(4.0);

    @Test void schemaValidCalibrationSurvivesActualProductionReadInBothLayers() throws Exception {
        for (Layer layer : Layer.values()) {
            short[] unity = read(layer, 0.0, emission(.08));
            double reference = rms(unity);
            for (double gainDb : new double[]{-FOUR_DB, 0.0, FOUR_DB, 24.0}) {
                short[] actual = read(layer, gainDb, emission(.08));
                double amplitude = Math.pow(10.0, gainDb / 20.0);
                double errorDb = 20.0 * Math.log10(rms(actual) / reference / amplitude);
                assertTrue(Math.abs(errorDb) <= .05,
                        layer + " calibration " + gainDb + " dB error=" + errorDb);
                assertTrue(java.util.stream.IntStream.range(0, actual.length)
                        .noneMatch(i -> Math.abs((int) actual[i]) == 32767), "control must not clip");
                assertArrayEquals(oracle(layer, amplitude, emission(.08)), actual);
            }
        }
    }

    @Test void unityKeepsTheOriginalPcmBytesAndClosedStreamsReturnNoSamples() throws Exception {
        for (Layer layer : Layer.values()) {
            var state = state(0.0, emission(.08));
            var stream = new ProceduralDroneAudioStream(state, layer, SEED);
            assertArrayEquals(oracle(layer, 1.0, emission(.08)), shorts(stream.read(FRAMES * 2)));
            stream.close();
            assertEquals(0, stream.read(2048).remaining());
        }
    }

    @Test void highCalibrationPreservesTheExplicitS16SaturationBoundary() throws Exception {
        for (Layer layer : Layer.values()) {
            short[] actual = read(layer, 24.0, emission(10.0));
            assertArrayEquals(oracle(layer, Math.pow(10.0, 24.0 / 20.0), emission(10.0)), actual);
            assertTrue(java.util.stream.IntStream.range(0, actual.length)
                    .anyMatch(i -> Math.abs((int) actual[i]) == 32767), "headroom probe must clip");
            assertTrue(java.util.stream.IntStream.range(0, actual.length)
                    .allMatch(i -> actual[i] != Short.MIN_VALUE));
        }
    }

    @Test void snapshotCarriesCalibrationAndWetSourceAcceptsTheEntireSchemaRange() throws Exception {
        var state = state(24.0, emission(.08));
        double maximum = Math.pow(10.0, 24.0 / 20.0);
        assertEquals(maximum, state.motorPlaybackAmplitude());
        assertEquals(maximum, state.propellerPlaybackAmplitude());
        assertTrue(state.replacesLegacyRpmVolume());
        var source = new ListenerReverbState.Source(157, emission(.08), maximum, maximum);
        assertEquals(maximum, source.motorAmplitude());
        for (double invalid : new double[]{-1.0, maximum + .01, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ListenerReverbState.Source(157, emission(.08), invalid, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new ListenerReverbState.Source(157, emission(.08), 0, invalid));
        }
    }

    private static short[] read(Layer layer, double gainDb, AcousticEmissionFrame emission) throws Exception {
        var stream = new ProceduralDroneAudioStream(state(gainDb, emission), layer, SEED);
        try { return shorts(stream.read(FRAMES * 2)); }
        finally { stream.close(); }
    }

    private static DroneAcousticRenderState state(double gainDb, AcousticEmissionFrame emission) throws Exception {
        var state = new DroneAcousticRenderState();
        var zero = new AcousticVector(0, 0, 0);
        var source = new AcousticSourceFrame(157, 0, zero, zero, new AcousticVector(0, 1, 0), .127,
                List.of(new RotorAcousticState(12_000, .5, .5, .0635, 3, 7, 1, 0)));
        var calibration = new AcousticSourceProfile.Calibration(true, true, gainDb, gainDb);
        var snapshot = new DroneAcousticRenderState.AudioSnapshot(source,
                new AcousticListenerFrame(new AcousticVector(0, 0, 1), zero),
                emission, emission, 1.0, 343.0, calibration);
        // Inject the immutable audio handoff, not a catalog profile with invented holdouts.
        var field = DroneAcousticRenderState.class.getDeclaredField("audioSnapshot");
        field.setAccessible(true); field.set(state, snapshot);
        return state;
    }

    private static short[] oracle(Layer layer, double amplitude, AcousticEmissionFrame emission) {
        float[] raw = new float[FRAMES];
        new PhaseContinuousSynthesizer(48_000, SEED).render(emission, layer, raw);
        short[] expected = new short[FRAMES];
        for (int i = 0; i < FRAMES; i++) {
            float calibrated = (float) (raw[i] * amplitude);
            expected[i] = (short) Math.round(Math.max(-1.0f, Math.min(1.0f, calibrated)) * 32767.0f);
        }
        return expected;
    }

    private static AcousticEmissionFrame emission(double amplitude) {
        return new AcousticEmissionFrame(List.of(
                new TonalComponent(TonalComponent.Kind.ELECTRICAL, 0, 1, 700, amplitude, 0),
                new TonalComponent(TonalComponent.Kind.BLADE_PASS, 0, 1, 1000, amplitude, 0)),
                AcousticBands.SILENT);
    }

    private static short[] shorts(java.nio.ByteBuffer data) {
        var values = data.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] result = new short[values.remaining()]; values.get(result); return result;
    }

    private static double rms(short[] values) {
        double energy = 0;
        for (int i = 12_000; i < 36_000; i++) energy += (double) values[i] * values[i];
        return Math.sqrt(energy / 24_000);
    }
}
