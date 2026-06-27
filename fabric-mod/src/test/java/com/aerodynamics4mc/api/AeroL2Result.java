package com.aerodynamics4mc.api;

import java.util.Arrays;

public final class AeroL2Result {
	private final String status;
	private final float[] flowAtlas;
	private final AeroL2ForceMoment forceMoment;
	private final String message;
	private final String runtimeInfo;

	private AeroL2Result(String status, float[] flowAtlas, AeroL2ForceMoment forceMoment, String message, String runtimeInfo) {
		this.status = status == null ? "FAILED" : status;
		this.flowAtlas = flowAtlas == null ? new float[0] : Arrays.copyOf(flowAtlas, flowAtlas.length);
		this.forceMoment = forceMoment;
		this.message = message == null ? "" : message;
		this.runtimeInfo = runtimeInfo == null ? "" : runtimeInfo;
	}

	public static AeroL2Result success(
			AeroL2Request request,
			float[] flowAtlas,
			AeroL2ForceMoment forceMoment,
			String runtimeInfo
	) {
		return new AeroL2Result("OK", flowAtlas, forceMoment, "", runtimeInfo);
	}

	public String status() {
		return status;
	}

	public boolean succeeded() {
		return "OK".equals(status);
	}

	public boolean available() {
		return !"UNAVAILABLE".equals(status);
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

	public String message() {
		return message;
	}

	public String runtimeInfo() {
		return runtimeInfo;
	}
}
