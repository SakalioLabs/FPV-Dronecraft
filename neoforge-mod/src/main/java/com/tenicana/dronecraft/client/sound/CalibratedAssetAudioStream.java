package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.PhaseContinuousSynthesizer;
import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.function.DoubleSupplier;
import java.util.function.BooleanSupplier;

/** Resource PCM calibration before AL gain, with optional internal native distance ownership. */
final class CalibratedAssetAudioStream implements InternalDistanceAudioStream {
    private final VerifiedAssetPcm.Cursor cursor;
    private final DoubleSupplier amplitude;
    private final DroneAcousticRenderState state;
    private final PhaseContinuousSynthesizer.Layer layer;
    private final boolean internalDistanceOwnership;
    private final CalibratedStreamLifetime lifetime;
    private volatile boolean closed;
    CalibratedAssetAudioStream(VerifiedAssetPcm asset, boolean repeat, DoubleSupplier amplitude) {
        this(asset,repeat,amplitude,null,null,false);
    }
    CalibratedAssetAudioStream(VerifiedAssetPcm asset, boolean repeat, DoubleSupplier amplitude,
                               DroneAcousticRenderState state, PhaseContinuousSynthesizer.Layer layer,
                               boolean internalDistanceOwnership) {
        this(asset,repeat,amplitude,state,layer,internalDistanceOwnership,
                new CalibratedStreamLifetime(state==null?new Object():state));
    }
    CalibratedAssetAudioStream(VerifiedAssetPcm asset, boolean repeat, DoubleSupplier amplitude,
                               DroneAcousticRenderState state, PhaseContinuousSynthesizer.Layer layer,
                               boolean internalDistanceOwnership, CalibratedStreamLifetime lifetime) {
        this.cursor=asset.cursor(repeat); this.amplitude=amplitude;
        this.state=state;this.layer=layer;this.internalDistanceOwnership=internalDistanceOwnership;
        this.lifetime=lifetime;
        if(internalDistanceOwnership) {
            if(state==null||layer==null)throw new IllegalArgumentException("internal asset distance state absent");
            state.registerDistanceStream(layer,this);
        }
        lifetime.register(this,this::close);
    }
    @Override public boolean internalDistanceOwnership() {return internalDistanceOwnership;}
    @Override public DroneAcousticRenderState distanceOwnershipMonitor() {return state;}
    @Override public boolean distanceOwnerMayConfigure() {return !closed&&state.distanceStreamMayConfigure(layer,this);}
    @Override public boolean distanceOwnerVerified() {return !closed&&state.distanceStreamVerified(layer,this);}
    @Override public boolean nativeDistanceOwnerConfigured(boolean verified) {return !closed&&state.configureDistanceStream(layer,this,verified);}
    @Override public AudioFormat getFormat() { return new AudioFormat(48_000,16,1,true,false); }
    @Override public ByteBuffer read(int byteCount) {
        synchronized(lifetime.monitor()) { return cursor.read(byteCount,amplitude.getAsDouble()); }
    }
    boolean playbackLive() { return !closed && lifetime.active(); }
    boolean attachWhileLive(Runnable original) {
        boolean attached=lifetime.runIfActive(()-> {if(closed)return false;original.run();return !closed;});
        if(!attached)close();
        return attached;
    }
    boolean playWhileLive(BooleanSupplier original) {
        return lifetime.runIfActive(()->!closed && original.getAsBoolean());
    }
    @Override public void close() {
        synchronized(lifetime.monitor()) {
            if(closed)return;
            closed=true;
            if(internalDistanceOwnership)state.closeDistanceStream(layer,this);
            cursor.close();
            lifetime.remove(this);
        }
    }
}
