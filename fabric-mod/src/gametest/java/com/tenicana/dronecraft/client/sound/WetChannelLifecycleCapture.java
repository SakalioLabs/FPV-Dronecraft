package com.tenicana.dronecraft.client.sound;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.tenicana.dronecraft.client.mixin.ChannelSourceAccessor;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

import java.util.IdentityHashMap;

/** Test-only actual Channel allocation and method-chain identity observer. */
public final class WetChannelLifecycleCapture {
    private static final IdentityHashMap<Object, JsonObject> allocations = new IdentityHashMap<>();
    private static final JsonArray allocationLog = new JsonArray(), calls = new JsonArray();
    private static int nextCall = 1;

    private WetChannelLifecycleCapture() { }

    public static synchronized void created(Object channel) {
        if (!WetMainOutputCapture.requested() || channel == null) return;
        boolean initialized;
        try {
            var field = channel.getClass().getDeclaredField("initialized"); field.setAccessible(true);
            initialized = ((java.util.concurrent.atomic.AtomicBoolean) field.get(channel)).get();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("actual Channel initialized state unavailable", error);
        }
        if (allocations.containsKey(channel) || allocations.size() >= 4096)
            throw new IllegalStateException("duplicate/exhausted actual Channel allocation recorder");
        JsonObject row = new JsonObject();
        row.addProperty("allocation_identity", allocations.size() + 1);
        row.addProperty("channel_identity", System.identityHashCode(channel));
        row.addProperty("source", ((ChannelSourceAccessor) channel).fpvdrone$getSource());
        long context = ALC10.alcGetCurrentContext(), device = ALC10.alcGetContextsDevice(context);
        row.addProperty("context", context); row.addProperty("device", device);
        row.addProperty("generation", MainContextOutputCapture.generation(device, context));
        row.addProperty("created_runtime_index", DistanceOwnershipRuntimeTrace.snapshot().size());
        row.addProperty("initialized", initialized);
        row.addProperty("source_valid", AL10.alIsSource(row.get("source").getAsInt()));
        row.addProperty("al_error", AL10.alGetError());
        row.addProperty("thread", Thread.currentThread().getName());
        if (!initialized || !row.get("source_valid").getAsBoolean() || row.get("al_error").getAsInt() != 0)
            throw new IllegalStateException("actual Channel create RETURN did not expose a valid fresh source");
        allocations.put(channel, row); allocationLog.add(row);
    }

    public static synchronized int identity(Object channel) {
        JsonObject row = allocations.get(channel);
        if (row == null) throw new IllegalStateException("Channel not observed at actual create RETURN");
        return row.get("allocation_identity").getAsInt();
    }

    public static synchronized JsonObject begin(Object channel, String method, String nativeMethod,
                                                 boolean initialized, Object stream) {
        JsonObject allocation = allocations.get(channel);
        if (allocation == null || calls.size() >= 4096)
            throw new IllegalStateException("unknown/exhausted actual Channel method recorder");
        JsonObject row = allocation.deepCopy();
        row.remove("created_runtime_index"); row.remove("initialized"); row.remove("source_valid"); row.remove("al_error");
        row.addProperty("call_identity", nextCall++); row.addProperty("method", method);
        row.addProperty("thread", Thread.currentThread().getName());
        row.addProperty("native_method", nativeMethod);
        row.addProperty("runtime_start", DistanceOwnershipRuntimeTrace.snapshot().size());
        row.addProperty("initialized_before", initialized);
        nativeState(row, "before");
        if (stream != null) {
            row.addProperty("stream_identity", System.identityHashCode(stream));
            row.addProperty("stream_class", stream.getClass().getName());
        }
        return row;
    }

    public static synchronized void end(JsonObject row, boolean initialized, Object stream) {
        row.addProperty("runtime_end", DistanceOwnershipRuntimeTrace.snapshot().size());
        row.addProperty("initialized_after", initialized);
        nativeState(row, "after");
        if (stream != null) {
            boolean recorded = stream instanceof ProceduralDroneAudioStream || stream instanceof ListenerReverbAudioStream
                    || (AssetNativeCapture.requested() && stream instanceof CalibratedAssetAudioStream);
            row.addProperty("input_record_available", recorded);
            if (recorded) row.addProperty("input_record_identity", MainContextOutputCapture.identity(stream));
            else row.add("input_record_identity", com.google.gson.JsonNull.INSTANCE);
        }
        calls.add(row);
    }

    private static void nativeState(JsonObject row, String suffix) {
        long context = ALC10.alcGetCurrentContext();
        long device = context == 0 ? 0 : ALC10.alcGetContextsDevice(context);
        row.addProperty("current_context_" + suffix, context);
        row.addProperty("current_device_" + suffix, device);
        boolean initialized = row.get("initialized_" + suffix).getAsBoolean();
        row.addProperty("source_query_performed_" + suffix, initialized);
        row.addProperty("source_valid_" + suffix,
                initialized && AL10.alIsSource(row.get("source").getAsInt()));
        int error = AL10.alGetError(); row.addProperty("al_error_" + suffix, error);
        if (context != row.get("context").getAsLong() || device != row.get("device").getAsLong() || error != 0)
            throw new IllegalStateException("Channel method was outside its actual main context or had native error");
    }

    public static synchronized JsonArray allocations() { return allocationLog.deepCopy(); }
    public static synchronized JsonArray calls() { return calls.deepCopy(); }
}
