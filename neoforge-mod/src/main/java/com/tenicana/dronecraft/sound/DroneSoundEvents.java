package com.tenicana.dronecraft.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneSoundEvents {
	private static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(
			Registries.SOUND_EVENT,
			FpvDronecraftMod.MOD_ID
	);
	private static final DeferredHolder<SoundEvent, SoundEvent> MOTOR_LOOP = registerFixedRange("drone.motor_loop", 48.0f);
	private static final DeferredHolder<SoundEvent, SoundEvent> PROPELLER_LOOP = registerFixedRange("drone.propeller_loop", 48.0f);
	private static final DeferredHolder<SoundEvent, SoundEvent> IMPACT = registerFixedRange("drone.impact", 32.0f);
	private static final DeferredHolder<SoundEvent, SoundEvent> LISTENER_REVERB_BUS = registerFixedRange("drone.listener_reverb_bus", 1.0f);
	private static final DeferredHolder<SoundEvent, SoundEvent> AUDIO_LAB_MARKER = registerFixedRange("drone.audio_lab_marker", 1.0f);

	private DroneSoundEvents() {
	}

	public static void register(IEventBus modEventBus) {
		SOUND_EVENTS.register(modEventBus);
	}

	public static SoundEvent motorLoop() {
		return MOTOR_LOOP.get();
	}

	public static SoundEvent propellerLoop() {
		return PROPELLER_LOOP.get();
	}

	public static SoundEvent impact() {
		return IMPACT.get();
	}
	public static SoundEvent listenerReverbBus() { return LISTENER_REVERB_BUS.get(); }
	public static SoundEvent audioLabMarker() { return AUDIO_LAB_MARKER.get(); }

	private static DeferredHolder<SoundEvent, SoundEvent> registerFixedRange(String name, float range) {
		return SOUND_EVENTS.register(name, () -> SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, name), range));
	}
}
