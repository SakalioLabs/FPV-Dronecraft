package com.tenicana.dronecraft.client.sound;

import com.google.gson.*;
import com.mojang.blaze3d.audio.Channel;
import com.tenicana.dronecraft.client.mixin.ChannelHandleChannelAccessor;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.AudioStream;
import java.util.*;
import java.util.function.Consumer;

/** Test stimulus admission, before resolution/allocation, with an executor-side phase guard. */
public final class WetSoundAdmissionCapture {
    private static final IdentityHashMap<SoundInstance, Integer> instances = new IdentityHashMap<>();
    private static final IdentityHashMap<SoundInstance, Boolean> exact = new IdentityHashMap<>();
    private static final IdentityHashMap<ChannelAccess.ChannelHandle, Ticket> handles = new IdentityHashMap<>();
    private static final ThreadLocal<Ticket> current = new ThreadLocal<>();
    private static final JsonArray phases = new JsonArray(), requests = new JsonArray(), deliveries = new JsonArray(), consumers = new JsonArray(), barriers = new JsonArray(), probes = new JsonArray();
    private static final IdentityHashMap<AudioStream, Ticket> deliveredStreams = new IdentityHashMap<>();
    private static long order = 0;
    private static int phaseIdentity = 0, bodies = 0, futures = 0, queued = 0;
    private static String phase = "BOOT_CLOSED", mode = "CLOSED";
    private static int requestSequence = 0, deliverySequence = 0, consumerSequence = 0;
    static { transition("BOOT_CLOSED", "CLOSED", List.of()); }
    public static final class Ticket {
        public final boolean allowed;
        final int phaseId;
        final JsonObject row;
        final Ticket previous;
        Ticket(boolean allowed, JsonObject row) {
            this.allowed = allowed; this.row = row; phaseId = phaseIdentity; previous = current.get();
        }
    }
    public static final class Delivery {
        final Ticket ticket; final JsonObject row;
        boolean complete;
        Delivery(Ticket ticket, JsonObject row) { this.ticket = ticket; this.row = row; }
        public <T> Consumer<T> wrap(Consumer<T> original) {
            return value -> {
                Ticket previous = current.get(); current.set(ticket);
                synchronized (WetSoundAdmissionCapture.class) {
                    row.addProperty("callback_entered", true); row.addProperty("callback_order", ++order);
                    if (value instanceof AudioStream stream) deliveredStreams.put(stream, ticket);
                }
                try { original.accept(value); }
                finally { if (previous == null) current.remove(); else current.set(previous); }
            };
        }
        public void complete(Throwable failure) {
            synchronized (WetSoundAdmissionCapture.class) {
                if (complete) throw new IllegalStateException("duplicate future delivery completion");
                complete = true; futures--; row.addProperty("completed", true);
                row.addProperty("terminal_order", ++order);
                row.addProperty("runtime_end", cursor());
                row.add("failure", failure == null ? JsonNull.INSTANCE : new JsonPrimitive(failure.toString()));
            }
        }
    }
    private WetSoundAdmissionCapture() { }
    private static int cursor() { return DistanceOwnershipRuntimeTrace.snapshot().size(); }
    private static void bounded() {
        if (requests.size() > 4096 || deliveries.size() > 4096 || consumers.size() > 16384)
            throw new IllegalStateException("admission recorder exhausted");
    }
    public static synchronized Ticket begin(SoundInstance sound) {
        CalibratedGainCapture.start(sound);
        bounded(); int instance = instances.computeIfAbsent(sound, ignored -> instances.size() + 1);
        boolean allowed = mode.equals("LIVE")
                ? sound instanceof DroneLoopSoundInstance || sound instanceof ListenerReverbSoundInstance
                : mode.equals("EXACT") && exact.containsKey(sound);
        JsonObject row = new JsonObject(); row.addProperty("request_identity", ++requestSequence);
        row.addProperty("phase_identity", phaseIdentity); row.addProperty("phase", phase); row.addProperty("mode", mode);
        row.addProperty("instance_identity", instance); row.addProperty("instance_object_hash", System.identityHashCode(sound));
        row.addProperty("instance_class", sound.getClass().getName()); row.addProperty("allowed", allowed);
        row.addProperty("runtime_start", cursor()); row.addProperty("thread", Thread.currentThread().getName());
        row.addProperty("begin_order", ++order);
        row.addProperty("original_calls", allowed ? 1 : 0); row.add("allocations", new JsonArray());
        row.addProperty("completed", false); row.add("failure", JsonNull.INSTANCE); requests.add(row);
        Ticket ticket = new Ticket(allowed, row);
        if (allowed) { bodies++; current.set(ticket); }
        else { row.addProperty("completed", true); row.addProperty("result", "NOT_STARTED"); row.addProperty("runtime_end", cursor()); row.addProperty("terminal_order", ++order); }
        return ticket;
    }
    public static synchronized void end(Ticket ticket, SoundEngine.PlayResult result, Throwable failure) {
        if (!ticket.allowed || ticket.row.get("completed").getAsBoolean()) throw new IllegalStateException("invalid play completion");
        bodies--; ticket.row.addProperty("completed", true); ticket.row.addProperty("runtime_end", cursor());
        ticket.row.addProperty("terminal_order", ++order);
        ticket.row.add("result", result == null ? JsonNull.INSTANCE : new JsonPrimitive(result.name()));
        ticket.row.add("failure", failure == null ? JsonNull.INSTANCE : new JsonPrimitive(failure.toString()));
        if (ticket.previous == null) current.remove(); else current.set(ticket.previous);
    }
    public static Ticket ticket() {
        Ticket result = current.get();
        if (result == null || !result.allowed) throw new IllegalStateException("allocation/delivery lacks admitted play ticket");
        return result;
    }
    public static synchronized ChannelAccess.ChannelHandle bind(Ticket ticket, ChannelAccess.ChannelHandle handle) {
        if (handle == null) return null;
        if (handles.put(handle, ticket) != null) throw new IllegalStateException("actual handle bound twice");
        Channel channel = ((ChannelHandleChannelAccessor) handle).fpvdrone$getChannel();
        if (channel == null) throw new IllegalStateException("new actual handle has no channel");
        JsonObject binding = new JsonObject(); binding.addProperty("handle_identity", System.identityHashCode(handle));
        binding.addProperty("allocation_identity", WetChannelLifecycleCapture.identity(channel));
        binding.addProperty("bind_order", ++order);
        binding.addProperty("runtime_index", cursor()); ticket.row.getAsJsonArray("allocations").add(binding);
        return handle;
    }
    public static synchronized Delivery delivery() {
        bounded(); Ticket ticket = ticket(); JsonObject row = new JsonObject();
        row.addProperty("delivery_identity", ++deliverySequence);
        row.addProperty("request_identity", ticket.row.get("request_identity").getAsInt());
        row.addProperty("phase_identity", ticket.phaseId); row.addProperty("runtime_start", cursor());
        row.addProperty("submit_order", ++order);
        row.addProperty("callback_entered", false); row.addProperty("completed", false); row.add("failure", JsonNull.INSTANCE);
        deliveries.add(row); futures++; return new Delivery(ticket, row);
    }
    public static synchronized Runnable guardTask(ChannelAccess.ChannelHandle handle, Runnable original) {
        bounded(); Ticket ticket = handles.get(handle);
        if (ticket == null) throw new IllegalStateException("actual executor handle has no admitted allocation");
        JsonObject row = new JsonObject(); row.addProperty("consumer_identity", ++consumerSequence);
        row.addProperty("request_identity", ticket.row.get("request_identity").getAsInt());
        row.addProperty("phase_identity", ticket.phaseId); row.addProperty("handle_identity", System.identityHashCode(handle));
        row.addProperty("runtime_start", cursor()); row.addProperty("completed", false); row.add("failure", JsonNull.INSTANCE);
        row.addProperty("submit_order", ++order);
        consumers.add(row); queued++;
        return () -> {
            synchronized (WetSoundAdmissionCapture.class) {
                boolean allowed = ticket.phaseId == phaseIdentity && !mode.equals("CLOSED");
                row.addProperty("execution_phase_identity", phaseIdentity); row.addProperty("executed", allowed);
                row.addProperty("execution_thread", Thread.currentThread().getName());
                row.addProperty("execution_order", ++order);
                row.addProperty("runtime_execute", cursor());
                try { if (allowed) original.run(); }
                catch (Throwable failure) { row.addProperty("failure", failure.toString()); throw failure; }
                finally { queued--; row.addProperty("completed", true); row.addProperty("runtime_end", cursor()); row.addProperty("terminal_order", ++order); }
            }
        };
    }
    public static synchronized void live() { transition("LIVE", "LIVE", List.of()); }
    public static synchronized void exact(String name, List<SoundInstance> sounds) {
        if (!drained()) throw new IllegalStateException("cannot open matrix with pending old executor work");
        transition("CASE:" + name, "EXACT", sounds);
    }
    public static synchronized void close(String reason) { transition("CLOSED:" + reason, "CLOSED", List.of()); }
    private static void transition(String name, String nextMode, List<SoundInstance> sounds) {
        phaseIdentity++; phase = name; mode = nextMode; exact.clear(); JsonArray ids = new JsonArray();
        for (SoundInstance sound : sounds) { exact.put(sound, true); ids.add(instances.computeIfAbsent(sound, ignored -> instances.size() + 1)); }
        JsonObject row = new JsonObject(); row.addProperty("phase_identity", phaseIdentity); row.addProperty("phase", phase);
        row.addProperty("begin_order", ++order);
        row.addProperty("mode", mode); row.add("exact_instances", ids); row.addProperty("runtime_index", cursor());
        row.addProperty("play_bodies_pending", bodies); row.addProperty("future_deliveries_pending", futures); row.addProperty("consumers_pending", queued);
        phases.add(row);
    }
    public static synchronized boolean drained() { return bodies == 0 && futures == 0 && queued == 0; }
    public static void reclaimUnattached() {
        java.util.List<AudioStream> candidates = new java.util.ArrayList<>();
        synchronized (WetSoundAdmissionCapture.class) {
          if (!drained() || !mode.equals("CLOSED")) throw new IllegalStateException("unattached stream cleanup before drain");
          for (var item : deliveredStreams.entrySet()) {
            AudioStream stream = item.getKey(); Ticket ticket = item.getValue(); boolean attached = false;
            for (var binding : handles.entrySet()) if (binding.getValue() == ticket) {
                Channel channel = ((ChannelHandleChannelAccessor) binding.getKey()).fpvdrone$getChannel();
                if (channel != null && ((com.tenicana.dronecraft.client.mixin.MainOutputChannelAccessor) channel).mainOutput$getStream() == stream) attached = true;
            }
            if (!attached) candidates.add(stream);
          }
        }
        for (AudioStream stream : candidates) {
            boolean closed = AssetNativeCapture.requested() ? AssetNativeCapture.closed(stream) : WetMainOutputCapture.closed(stream);
            if (closed) continue;
            try { stream.close(); }
            catch (Exception error) { throw new IllegalStateException("unattached delivered stream cleanup failed", error); }
        }
    }
    public static synchronized void barrier(int actualHandles) {
        if (!drained() || !mode.equals("CLOSED") || actualHandles != 0) throw new IllegalStateException("phase barrier is not closed and empty");
        JsonObject row = new JsonObject(); row.addProperty("phase_identity", phaseIdentity); row.addProperty("order", ++order);
        row.addProperty("runtime_index", cursor()); row.addProperty("play_bodies_pending", bodies);
        row.addProperty("future_deliveries_pending", futures); row.addProperty("consumers_pending", queued);
        row.addProperty("post_stop_handles", actualHandles); barriers.add(row);
    }
    public static synchronized void probe(String kind, SoundInstance sound, SoundEngine.PlayResult result) {
        JsonObject request = null; int instance = instances.get(sound);
        for (var value : requests) if (value.getAsJsonObject().get("instance_identity").getAsInt() == instance) request = value.getAsJsonObject();
        if (request == null) throw new IllegalStateException("probe has no actual admission request");
        JsonObject row = new JsonObject(); row.addProperty("kind", kind); row.addProperty("request_identity", request.get("request_identity").getAsInt());
        row.addProperty("result", result.name()); row.addProperty("order", ++order); probes.add(row);
    }
    public static synchronized JsonObject snapshot() {
        JsonObject result = new JsonObject(); result.addProperty("schema_version", 1); result.add("phases", phases.deepCopy());
        result.add("requests", requests.deepCopy()); result.add("deliveries", deliveries.deepCopy()); result.add("consumers", consumers.deepCopy());
        result.add("barriers", barriers.deepCopy()); result.add("probes", probes.deepCopy()); result.addProperty("async_quarantine_exercised", false);
        result.addProperty("play_bodies_pending", bodies); result.addProperty("future_deliveries_pending", futures); result.addProperty("consumers_pending", queued);
        return result;
    }
}
