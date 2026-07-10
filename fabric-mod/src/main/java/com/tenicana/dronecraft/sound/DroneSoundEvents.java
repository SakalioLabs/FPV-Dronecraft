package com.tenicana.dronecraft.sound;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import com.tenicana.dronecraft.FpvDronecraftMod;

public final class DroneSoundEvents {
	public static final SoundEvent MOTOR_LOOP = registerFixedRange("drone.motor_loop", 48.0f);
	public static final SoundEvent PROPELLER_LOOP = registerFixedRange("drone.propeller_loop", 48.0f);
	public static final SoundEvent IMPACT = registerFixedRange("drone.impact", 32.0f);

	private DroneSoundEvents() {
	}

	public static void initialize() {
		// Loading this class registers the sound events.
	}

	private static SoundEvent registerFixedRange(String name, float range) {
		Identifier identifier = Identifier.fromNamespaceAndPath(FpvDronecraftMod.MOD_ID, name);
		return Registry.register(
				BuiltInRegistries.SOUND_EVENT,
				identifier,
				SoundEvent.createFixedRangeEvent(identifier, range)
		);
	}
}
