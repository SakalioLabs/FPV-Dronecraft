package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;
import com.tenicana.dronecraft.sim.DroneConfig;

class RotorLayoutRenderCacheTest {
	@Test
	void reusesSnapshotsForEqualStringsAndRefreshesWhenStringChanges() {
		RotorLayoutRenderCache cache = new RotorLayoutRenderCache();
		String quad = RotorLayoutCodec.encode(DroneConfig.racingQuad());
		String hex = RotorLayoutCodec.encode(DroneConfig.hexLift());

		RotorLayoutRenderCache.Snapshot first = cache.get(quad);
		assertSame(first, cache.get(new String(quad)));
		RotorLayoutRenderCache.Snapshot changed = cache.get(hex);

		assertNotSame(first, changed);
		assertEquals(4, first.rotorCount());
		assertEquals(6, changed.rotorCount());
		assertEquals(RotorLayoutCodec.decode(quad).xModelUnits(0), first.xModelUnits(0), 0.0f);
		assertEquals(RotorLayoutCodec.decode(hex).spinDirection(5), changed.spinDirection(5));
	}

	@Test
	void malformedAndNullStringsKeepCodecFallbackAndCacheNormally() {
		RotorLayoutRenderCache cache = new RotorLayoutRenderCache();

		RotorLayoutRenderCache.Snapshot malformed = cache.get("6;bad");
		RotorLayoutRenderCache.Snapshot missing = cache.get(null);

		assertEquals(RotorLayoutCodec.decode("6;bad").rotorCount(), malformed.rotorCount());
		assertEquals(RotorLayoutCodec.decode(null).xModelUnits(0), missing.xModelUnits(0), 0.0f);
		assertSame(malformed, cache.get("6;bad"));
		assertSame(missing, cache.get(null));
	}

	@Test
	void evictsLeastRecentlyUsedEntryAtCapacity() {
		RotorLayoutRenderCache cache = new RotorLayoutRenderCache();
		String[] keys = new String[RotorLayoutRenderCache.CAPACITY + 1];
		RotorLayoutRenderCache.Snapshot[] originals = new RotorLayoutRenderCache.Snapshot[keys.length];
		for (int index = 0; index < RotorLayoutRenderCache.CAPACITY; index++) {
			keys[index] = "1;" + index + ",0,0,1";
			originals[index] = cache.get(keys[index]);
		}
		cache.get(keys[0]);
		keys[RotorLayoutRenderCache.CAPACITY] = "1;" + RotorLayoutRenderCache.CAPACITY + ",0,0,1";
		originals[RotorLayoutRenderCache.CAPACITY] = cache.get(keys[RotorLayoutRenderCache.CAPACITY]);

		assertEquals(RotorLayoutRenderCache.CAPACITY, cache.size());
		assertSame(originals[0], cache.get(keys[0]));
		assertNotSame(originals[1], cache.get(keys[1]));
		assertEquals(RotorLayoutRenderCache.CAPACITY, cache.size());
	}
}
