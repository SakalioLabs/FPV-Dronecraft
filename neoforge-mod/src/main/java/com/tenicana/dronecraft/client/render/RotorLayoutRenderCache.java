package com.tenicana.dronecraft.client.render;

import java.util.LinkedHashMap;
import java.util.Map;

import com.tenicana.dronecraft.entity.RotorLayoutCodec;

/** Small renderer-local cache for decoded, stable entity rotor layout strings. */
final class RotorLayoutRenderCache {
	static final int CAPACITY = 8;

	private final LinkedHashMap<String, Snapshot> layouts = new LinkedHashMap<>(CAPACITY, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Snapshot> eldest) {
			return size() > CAPACITY;
		}
	};

	Snapshot get(String encoded) {
		return layouts.computeIfAbsent(encoded, RotorLayoutRenderCache::decode);
	}

	int size() {
		return layouts.size();
	}

	private static Snapshot decode(String encoded) {
		RotorLayoutCodec.Layout decoded = RotorLayoutCodec.decode(encoded);
		float[] x = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
		float[] y = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
		float[] z = new float[RotorLayoutCodec.MAX_RENDER_ROTORS];
		int[] spin = new int[RotorLayoutCodec.MAX_RENDER_ROTORS];
		for (int index = 0; index < x.length; index++) {
			x[index] = decoded.xModelUnits(index);
			y[index] = decoded.yModelUnits(index);
			z[index] = decoded.zModelUnits(index);
			spin[index] = decoded.spinDirection(index);
		}
		return new Snapshot(decoded.rotorCount(), x, y, z, spin);
	}

	static final class Snapshot {
		private final int rotorCount;
		private final float[] x;
		private final float[] y;
		private final float[] z;
		private final int[] spin;

		private Snapshot(int rotorCount, float[] x, float[] y, float[] z, int[] spin) {
			this.rotorCount = rotorCount;
			this.x = x;
			this.y = y;
			this.z = z;
			this.spin = spin;
		}

		int rotorCount() { return rotorCount; }
		float xModelUnits(int index) { return index >= 0 && index < rotorCount ? x[index] : 0.0f; }
		float yModelUnits(int index) { return index >= 0 && index < rotorCount ? y[index] : 0.0f; }
		float zModelUnits(int index) { return index >= 0 && index < rotorCount ? z[index] : 0.0f; }
		int spinDirection(int index) { return index >= 0 && index < rotorCount ? spin[index] : 1; }
	}
}
