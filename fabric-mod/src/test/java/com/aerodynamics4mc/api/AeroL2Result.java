package com.aerodynamics4mc.api;

import java.util.Arrays;

public final class AeroL2Result {
	private final float[] flowAtlas;
	private final AeroL2ForceMoment forceMoment;

	private AeroL2Result(float[] flowAtlas, AeroL2ForceMoment forceMoment) {
		this.flowAtlas = flowAtlas == null ? new float[0] : Arrays.copyOf(flowAtlas, flowAtlas.length);
		this.forceMoment = forceMoment;
	}

	public static AeroL2Result success(
			AeroL2Request request,
			float[] flowAtlas,
			AeroL2ForceMoment forceMoment,
			String runtimeInfo
	) {
		return new AeroL2Result(flowAtlas, forceMoment);
	}

	public boolean hasFlowAtlas() {
		return flowAtlas.length > 0;
	}

	public float[] flowAtlas() {
		return Arrays.copyOf(flowAtlas, flowAtlas.length);
	}

	public int atlasValueCount() {
		return flowAtlas.length;
	}

	public float[] velocityAt(int x, int y, int z) {
		return new float[] {0.0f, 0.0f, 0.0f};
	}

	public float pressureAt(int x, int y, int z) {
		return 0.0f;
	}

	public boolean hasForceMoment() {
		return forceMoment != null;
	}

	public AeroL2ForceMoment forceMoment() {
		return forceMoment;
	}
}
