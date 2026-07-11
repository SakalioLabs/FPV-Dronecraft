package com.tenicana.dronecraft.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
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

	private static DeferredHolder<SoundEvent, SoundEvent> registerFixedRange(String name, float range) {
		return SOUND_EVENTS.register(name, identifier -> SoundEvent.createFixedRangeEvent(identifier, range));
	}
}
