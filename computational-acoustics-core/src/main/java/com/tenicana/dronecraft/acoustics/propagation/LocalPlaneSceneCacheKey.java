package com.tenicana.dronecraft.acoustics.propagation;

/**
 * Allocation-free exact cache identity for a captured local-plane scene.
 * Any source/listener movement or snapshot generation change invalidates the
 * solved paths; later approximate reuse must be separately bounded and tested.
 */
public final class LocalPlaneSceneCacheKey {
	private boolean initialized;
	private long generation;
	private long sourceX;
	private long sourceY;
	private long sourceZ;
	private long listenerX;
	private long listenerY;
	private long listenerZ;

	public boolean matches(
			long candidateGeneration,
			double candidateSourceX,
			double candidateSourceY,
			double candidateSourceZ,
			double candidateListenerX,
			double candidateListenerY,
			double candidateListenerZ
	) {
		requireFinite(
				candidateSourceX,
				candidateSourceY,
				candidateSourceZ,
				"source"
		);
		requireFinite(
				candidateListenerX,
				candidateListenerY,
				candidateListenerZ,
				"listener"
		);
		return initialized
				&& generation == candidateGeneration
				&& sourceX == bits(candidateSourceX)
				&& sourceY == bits(candidateSourceY)
				&& sourceZ == bits(candidateSourceZ)
				&& listenerX == bits(candidateListenerX)
				&& listenerY == bits(candidateListenerY)
				&& listenerZ == bits(candidateListenerZ);
	}

	public void update(
			long nextGeneration,
			double nextSourceX,
			double nextSourceY,
			double nextSourceZ,
			double nextListenerX,
			double nextListenerY,
			double nextListenerZ
	) {
		requireFinite(nextSourceX, nextSourceY, nextSourceZ, "source");
		requireFinite(
				nextListenerX,
				nextListenerY,
				nextListenerZ,
				"listener"
		);
		generation = nextGeneration;
		sourceX = bits(nextSourceX);
		sourceY = bits(nextSourceY);
		sourceZ = bits(nextSourceZ);
		listenerX = bits(nextListenerX);
		listenerY = bits(nextListenerY);
		listenerZ = bits(nextListenerZ);
		initialized = true;
	}

	public void invalidate() {
		initialized = false;
	}

	private static long bits(double value) {
		return Double.doubleToLongBits(value == 0.0 ? 0.0 : value);
	}

	private static void requireFinite(
			double x,
			double y,
			double z,
			String label
	) {
		if (!Double.isFinite(x)
				|| !Double.isFinite(y)
				|| !Double.isFinite(z)) {
			throw new IllegalArgumentException(
					label + " coordinates must be finite"
			);
		}
	}
}
