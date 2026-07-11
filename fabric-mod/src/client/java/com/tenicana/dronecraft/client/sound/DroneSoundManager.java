package com.tenicana.dronecraft.client.sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.sound.DroneSoundPhysics;

public final class DroneSoundManager {
	private static final int MAX_ACTIVE_DRONES = 6;
	private static final double START_DISTANCE_SQUARED = 48.0 * 48.0;
	private static final double RELEASE_DISTANCE_SQUARED = 64.0 * 64.0;
	private static final int FADE_OUT_TICKS = 20;
	private static final int RESTART_CHECK_INTERVAL_TICKS = 20;
	private static final Map<Integer, DroneSoundSet> ACTIVE_SOUNDS = new HashMap<>();
	private static ClientLevel activeLevel;

	private DroneSoundManager() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(DroneSoundManager::tick);
		ClientLifecycleEvents.CLIENT_STOPPING.register(DroneSoundManager::stopAll);
	}

	private static void tick(Minecraft client) {
		if (client.level == null) {
			stopAll(client);
			activeLevel = null;
			return;
		}
		if (client.level != activeLevel) {
			stopAll(client);
			activeLevel = client.level;
		}
		if (client.options.getFinalSoundSourceVolume(SoundSource.NEUTRAL) <= 0.0f) {
			stopAll(client);
			return;
		}

		Entity listener = client.getCameraEntity() != null ? client.getCameraEntity() : client.player;
		if (listener == null) {
			stopAll(client);
			return;
		}

		List<DroneCandidate> candidates = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof DroneEntity drone) || !drone.isAlive() || drone.isRemoved()) {
				continue;
			}
			double distanceSquared = drone.distanceToSqr(listener);
			if (drone.isSilent()
					|| distanceSquared > START_DISTANCE_SQUARED
					|| !DroneSoundPhysics.isAudible(drone.getAverageMotorRpm())) {
				continue;
			}
			candidates.add(new DroneCandidate(drone, distanceSquared));
		}
		candidates.sort(Comparator.comparingDouble(DroneCandidate::distanceSquared));

		Set<Integer> selected = new HashSet<>();
		int selectedCount = Math.min(MAX_ACTIVE_DRONES, candidates.size());
		for (int index = 0; index < selectedCount; index++) {
			selected.add(candidates.get(index).drone().getId());
		}
		for (int index = 0; index < selectedCount; index++) {
			DroneEntity drone = candidates.get(index).drone();
			DroneSoundSet current = ACTIVE_SOUNDS.get(drone.getId());
			if (current != null && current.drone() != drone) {
				current.stop(client.getSoundManager());
				ACTIVE_SOUNDS.remove(drone.getId());
				current = null;
			}
			if (current == null) {
				makeRoomForSelectedSound(client.getSoundManager(), selected);
				if (ACTIVE_SOUNDS.size() >= MAX_ACTIVE_DRONES) {
					continue;
				}
				current = new DroneSoundSet(drone);
				ACTIVE_SOUNDS.put(drone.getId(), current);
			}
			current.ensureActive(client.getSoundManager());
		}

		Iterator<Map.Entry<Integer, DroneSoundSet>> iterator = ACTIVE_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, DroneSoundSet> entry = iterator.next();
			if (selected.contains(entry.getKey())) {
				continue;
			}
			DroneSoundSet sounds = entry.getValue();
			DroneEntity drone = sounds.drone();
			boolean valid = drone.level() == client.level
					&& drone.isAlive()
					&& !drone.isRemoved()
					&& !drone.isSilent();
			double distanceSquared = valid ? drone.distanceToSqr(listener) : Double.POSITIVE_INFINITY;
			boolean audible = valid && DroneSoundPhysics.isAudible(drone.getAverageMotorRpm());
			if (audible && distanceSquared <= RELEASE_DISTANCE_SQUARED) {
				continue;
			}
			boolean canFade = valid && distanceSquared <= RELEASE_DISTANCE_SQUARED && !audible;
			if (canFade && sounds.continueFadeOut()) {
				continue;
			}
			sounds.stop(client.getSoundManager());
			iterator.remove();
		}
	}

	private static void makeRoomForSelectedSound(SoundManager soundManager, Set<Integer> selected) {
		if (ACTIVE_SOUNDS.size() < MAX_ACTIVE_DRONES) {
			return;
		}
		Iterator<Map.Entry<Integer, DroneSoundSet>> iterator = ACTIVE_SOUNDS.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<Integer, DroneSoundSet> entry = iterator.next();
			if (selected.contains(entry.getKey())) {
				continue;
			}
			entry.getValue().stop(soundManager);
			iterator.remove();
			return;
		}
	}

	private static void stopAll(Minecraft client) {
		SoundManager soundManager = client.getSoundManager();
		for (DroneSoundSet sounds : ACTIVE_SOUNDS.values()) {
			sounds.stop(soundManager);
		}
		ACTIVE_SOUNDS.clear();
	}

	private static final class DroneSoundSet {
		private final DroneEntity drone;
		private DroneLoopSoundInstance motor;
		private DroneLoopSoundInstance propeller;
		private int restartCheckTicks;
		private int fadeOutTicks;

		private DroneSoundSet(DroneEntity drone) {
			this.drone = drone;
		}

		private DroneEntity drone() {
			return drone;
		}

		private void ensureActive(SoundManager soundManager) {
			fadeOutTicks = 0;
			if (restartCheckTicks > 0) {
				restartCheckTicks--;
				return;
			}
			restartCheckTicks = RESTART_CHECK_INTERVAL_TICKS;
			motor = ensureLayer(soundManager, motor, DroneLoopSoundInstance.Layer.MOTOR);
			propeller = ensureLayer(soundManager, propeller, DroneLoopSoundInstance.Layer.PROPELLER);
		}

		private DroneLoopSoundInstance ensureLayer(
				SoundManager soundManager,
				DroneLoopSoundInstance current,
				DroneLoopSoundInstance.Layer layer
		) {
			if (current != null && soundManager.isActive(current)) {
				return current;
			}
			stopLayer(soundManager, current);
			if (drone.isSilent()) {
				return null;
			}

			DroneLoopSoundInstance replacement = new DroneLoopSoundInstance(drone, layer);
			SoundEngine.PlayResult result = soundManager.play(replacement);
			if (result == SoundEngine.PlayResult.NOT_STARTED) {
				replacement.end();
				return null;
			}
			return replacement;
		}

		private boolean continueFadeOut() {
			fadeOutTicks++;
			return fadeOutTicks <= FADE_OUT_TICKS;
		}

		private void stop(SoundManager soundManager) {
			stopLayer(soundManager, motor);
			stopLayer(soundManager, propeller);
			motor = null;
			propeller = null;
		}

		private static void stopLayer(SoundManager soundManager, DroneLoopSoundInstance sound) {
			if (sound == null) {
				return;
			}
			sound.end();
			soundManager.stop(sound);
		}
	}

	private record DroneCandidate(DroneEntity drone, double distanceSquared) {
	}
}
