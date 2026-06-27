package com.aerodynamics4mc.api;

public record AeroL2ForceMoment(
		float forceX,
		float forceY,
		float forceZ,
		float momentX,
		float momentY,
		float momentZ,
		float centerOfPressureX,
		float centerOfPressureY,
		float centerOfPressureZ,
		float referenceX,
		float referenceY,
		float referenceZ
) {
}
