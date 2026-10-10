package com.tenicana.dronecraft.client.sound;

import net.minecraft.client.sounds.JOrbisAudioStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFormat;

/** Captures encoded bytes once; hash and decoder see the same immutable copy. */
final class CalibratedAssetDecoder {
    static final int MAX_ENCODED_BYTES=16*1024*1024;
    private CalibratedAssetDecoder() { }
    static VerifiedAssetPcm decode(AudioAssetCalibration calibration, byte[] encoded) throws IOException {
        byte[] captured=encoded.clone();
        if (captured.length<1 || captured.length>MAX_ENCODED_BYTES
                || !VerifiedAssetPcm.sha256(captured).equals(calibration.encodedSha256()))
            throw new IOException("encoded calibrated asset identity/size mismatch");
        try (var decoder=new JOrbisAudioStream(new ByteArrayInputStream(captured))) {
            AudioFormat f=decoder.getFormat();
            if (f.getSampleRate()!=48_000 || f.getChannels()!=1 || f.getSampleSizeInBits()!=16
                    || f.isBigEndian() || !f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED))
                throw new IOException("calibrated asset must decode to mono signed S16LE 48000 Hz");
            ByteArrayOutputStream pcm=new ByteArrayOutputStream();
            while (true) {
                var chunk=decoder.read(8192);
                if (!chunk.hasRemaining()) break;
                if ((chunk.remaining()&1)!=0 || pcm.size()+chunk.remaining()>calibration.frames()*2)
                    throw new IOException("decoded asset length outside declared reference");
                byte[] bytes=new byte[chunk.remaining()]; chunk.get(bytes); pcm.writeBytes(bytes);
            }
            return VerifiedAssetPcm.verify(calibration,pcm.toByteArray());
        }
    }
}
