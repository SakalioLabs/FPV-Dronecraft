package com.aerodynamics4mc.api;

public final class AeroWindApi {
	private AeroWindApi() {
	}

	public static AeroL2Result runL2(AeroL2Request request) {
		return AeroL2Result.success(request, new float[0], null, "test");
	}
}
