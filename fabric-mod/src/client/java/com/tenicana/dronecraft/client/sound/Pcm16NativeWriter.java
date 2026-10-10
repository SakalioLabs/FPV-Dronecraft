package com.tenicana.dronecraft.client.sound;

import static org.lwjgl.system.MemoryUtil.memPutShort;

/** The queued stream's float calibration, clamp and rounding, without a buffer wrapper. */
final class Pcm16NativeWriter {
    private Pcm16NativeWriter() { }

    static void write(float[] input, int frames, double amplitude, long output) {
        for (int index = 0; index < frames; index++) {
            float sample = (float) (input[index] * amplitude);
            int pcm = Math.round(Math.max(-1.0f, Math.min(1.0f, sample)) * 32767.0f);
            memPutShort(output + 2L * index, (short) pcm);
        }
    }

    static boolean validRequest(long samples, int bytes) {
        return samples != 0 && bytes > 0 && (bytes & 1) == 0
                && bytes <= CallbackPcmStream.MAXIMUM_CALLBACK_FRAMES * Short.BYTES;
    }
}
