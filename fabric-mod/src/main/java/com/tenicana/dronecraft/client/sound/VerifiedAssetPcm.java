package com.tenicana.dronecraft.client.sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Immutable decoded samples verified before any calibrated stream can be published. */
final class VerifiedAssetPcm {
    private final AudioAssetCalibration calibration;
    private final short[] samples;
    private final long maximumQuantizedAbsoluteSample;
    private VerifiedAssetPcm(AudioAssetCalibration calibration, short[] samples,
                             long maximumQuantizedAbsoluteSample) {
        this.calibration=calibration; this.samples=samples;
        this.maximumQuantizedAbsoluteSample=maximumQuantizedAbsoluteSample;
    }
    static VerifiedAssetPcm verify(AudioAssetCalibration reference, byte[] pcm) {
        if (pcm.length != reference.frames()*2 || !sha256(pcm).equals(reference.decodedSha256()))
            throw new IllegalArgumentException("decoded PCM identity/length mismatch");
        var shorts=ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        short[] samples=new short[shorts.remaining()]; shorts.get(samples);
        double squares=0;
        for (int i=reference.referenceStartFrame(); i<reference.referenceStartFrame()+reference.referenceFrameCount(); i++) {
            double sample=samples[i]/32768.0; squares+=sample*sample;
        }
        double rms=Math.sqrt(squares/reference.referenceFrameCount());
        if (Math.abs(rms-reference.referenceDigitalRms()) > 1e-8+1e-6*reference.referenceDigitalRms())
            throw new IllegalArgumentException("decoded reference RMS mismatch");
        double maximum=reference.maximumAmplitude();
        for (short sample:samples) if (sample*maximum >= 32767 || sample*maximum <= -32768)
            throw new IllegalArgumentException("asset response exceeds S16 headroom");
        // Include exactly the amplitude tolerance accepted by Cursor.read. In
        // particular a negative half-integer can round one LSB further from zero.
        double acceptedMaximum=maximum*(1+1e-12);
        long maximumQuantizedAbsoluteSample=0;
        for (short sample:samples) {
            long quantized=Math.round(sample*acceptedMaximum);
            maximumQuantizedAbsoluteSample=Math.max(maximumQuantizedAbsoluteSample,Math.abs(quantized));
        }
        return new VerifiedAssetPcm(reference,samples,maximumQuantizedAbsoluteSample);
    }
    AudioAssetCalibration calibration() { return calibration; }
    long maximumQuantizedAbsoluteSample() { return maximumQuantizedAbsoluteSample; }
    Cursor cursor(boolean repeat) { return new Cursor(repeat); }
    static String sha256(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    final class Cursor {
        private final boolean repeat;
        private int index;
        private boolean closed;
        private Cursor(boolean repeat) { this.repeat=repeat; }
        ByteBuffer read(int byteCount, double amplitude) {
            if (byteCount<0 || byteCount>1_048_576) throw new IllegalArgumentException("PCM read size outside [0,1MiB]");
            if (!Double.isFinite(amplitude) || amplitude<0 || amplitude>calibration.maximumAmplitude()*(1+1e-12))
                throw new IllegalArgumentException("asset gain outside verified headroom");
            int count=closed ? 0 : byteCount/2;
            if (!repeat) count=Math.min(count,samples.length-index);
            ByteBuffer result=ByteBuffer.allocateDirect(count*2).order(ByteOrder.LITTLE_ENDIAN);
            for (int i=0;i<count;i++) {
                if (index==samples.length) index=0;
                result.putShort((short)Math.round(samples[index++]*amplitude));
            }
            return result.flip();
        }
        void close() { closed=true; }
    }
}
