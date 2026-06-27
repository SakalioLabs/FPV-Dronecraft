package com.aerodynamics4mc.api;

public final class AeroWindApi {
	private AeroWindApi() {
	}

	public static AeroL2Result runL2(AeroL2Request request) {
		AeroL2ForceMoment forceMoment = request.computeForceMoment()
				? new AeroL2ForceMoment(
						1.25f,
						-0.50f,
						3.00f,
						0.20f,
						-0.10f,
						0.40f,
						request.referenceX() + 0.10f,
						request.referenceY() - 0.20f,
						request.referenceZ() + 0.30f,
						request.referenceX(),
						request.referenceY(),
						request.referenceZ()
				)
				: null;
		float[] flowAtlas = request.outputFlowAtlas() ? new float[] {request.inletVx(), request.inletVy(), request.inletVz(), 0.0f} : new float[0];
		return AeroL2Result.success(request, flowAtlas, forceMoment, "test-runtime");
	}
}
