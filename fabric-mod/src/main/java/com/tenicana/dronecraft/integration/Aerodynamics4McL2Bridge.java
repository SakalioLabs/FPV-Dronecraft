package com.tenicana.dronecraft.integration;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;

public final class Aerodynamics4McL2Bridge {
	private static final String MOD_ID = "aerodynamics4mc";
	private static final String WIND_API_CLASS = "com.aerodynamics4mc.api.AeroWindApi";
	private static final String L2_REQUEST_CLASS = "com.aerodynamics4mc.api.AeroL2Request";
	private static final String L2_RESULT_CLASS = "com.aerodynamics4mc.api.AeroL2Result";
	private static final String L2_FORCE_MOMENT_CLASS = "com.aerodynamics4mc.api.AeroL2ForceMoment";
	private static final int MIN_GRID_CELLS_PER_AXIS = 4;
	private static final int MAX_GRID_CELLS_PER_AXIS = 128;
	private static final int MAX_GRID_CELLS = 128 * 128 * 128;
	private static final int MAX_SOLVE_STEPS = 1_000;
	private static final int MAX_SAMPLE_STRIDE = 64;
	private static final double MIN_CELL_SIZE_METERS = 0.02;
	private static final double MAX_CELL_SIZE_METERS = 4.0;
	private static final double MIN_TIME_STEP_SECONDS = 0.0005;
	private static final double MAX_TIME_STEP_SECONDS = 0.05;
	private static final double MAX_INLET_SPEED_METERS_PER_SECOND = 80.0;
	private static final double MIN_AIR_DENSITY_KG_M3 = 0.5;
	private static final double MAX_AIR_DENSITY_KG_M3 = 1.6;
	private static final double MIN_AIR_KINEMATIC_VISCOSITY_M2_S = 5.0e-6;
	private static final double MAX_AIR_KINEMATIC_VISCOSITY_M2_S = 8.0e-5;
	private static final double DEFAULT_AIR_DENSITY_KG_M3 = 1.225;
	private static final double DEFAULT_AIR_KINEMATIC_VISCOSITY_M2_S = 1.5e-5;
	private static final double DEFAULT_TIME_STEP_SECONDS = 0.05;
	private static volatile L2Capabilities capabilities;

	private Aerodynamics4McL2Bridge() {
	}

	public static L2Capabilities capabilities() {
		L2Capabilities current = capabilities;
		if (current != null) {
			return current;
		}
		synchronized (Aerodynamics4McL2Bridge.class) {
			if (capabilities == null) {
				capabilities = initializeCapabilities();
			}
			return capabilities;
		}
	}

	public static L2RequestBuildResult buildRequest(L2RequestSpec spec) {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return L2RequestBuildResult.failure(
					null,
					L2Capabilities.unavailable(false, "aerodynamics4mc mod is not loaded"),
					"aerodynamics4mc mod is not loaded"
			);
		}
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) {
			loader = Aerodynamics4McL2Bridge.class.getClassLoader();
		}
		return buildRequest(loader, spec);
	}

	private static L2Capabilities initializeCapabilities() {
		if (!FabricLoader.getInstance().isModLoaded(MOD_ID)) {
			return L2Capabilities.unavailable(false, "aerodynamics4mc mod is not loaded");
		}
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		if (loader == null) {
			loader = Aerodynamics4McL2Bridge.class.getClassLoader();
		}
		return inspect(loader);
	}

	static L2RequestBuildResult buildRequest(ClassLoader loader, L2RequestSpec spec) {
		String validationError = validateSpec(spec);
		if (validationError != null) {
			return L2RequestBuildResult.failure(spec, L2Capabilities.notInspected(), validationError);
		}
		L2Capabilities currentCapabilities = inspect(loader);
		if (!currentCapabilities.available()) {
			return L2RequestBuildResult.failure(spec, currentCapabilities, currentCapabilities.message());
		}
		if (spec.computeForceMoment() && !currentCapabilities.forceMomentAvailable()) {
			return L2RequestBuildResult.failure(spec, currentCapabilities, "A4MC L2 force/moment API is not available");
		}
		if ((spec.solidMask() != null || spec.seedInitialUniformFlow()) && !currentCapabilities.requestMaskAvailable()) {
			return L2RequestBuildResult.failure(spec, currentCapabilities, "A4MC L2 mask/initial-flow API is not available");
		}
		try {
			Class<?> requestClass = Class.forName(L2_REQUEST_CLASS, false, loader);
			Method builderMethod = requestClass.getMethod("builder", int.class, int.class, int.class);
			Object builder = builderMethod.invoke(null, spec.nx(), spec.ny(), spec.nz());
			Class<?> builderClass = builderMethod.getReturnType();
			invokeBuilder(builderClass, builder, "cellSizeMeters", float.class, (float) spec.cellSizeMeters());
			invokeBuilder(builderClass, builder, "timeStepSeconds", float.class, (float) spec.timeStepSeconds());
			invokeBuilder(builderClass, builder, "steps", int.class, spec.steps());
			invokeBuilder(builderClass, builder, "sampleStride", int.class, spec.sampleStride());
			invokeBuilder(builderClass, builder, "inlet", new Class<?>[] {float.class, float.class, float.class},
					(float) spec.inletVx(), (float) spec.inletVy(), (float) spec.inletVz());
			invokeBuilder(builderClass, builder, "air", new Class<?>[] {float.class, float.class},
					(float) spec.densityKgM3(), (float) spec.kinematicViscosityM2S());
			byte[] solidMask = spec.solidMask();
			if (solidMask != null) {
				invokeBuilder(builderClass, builder, "solidMask", byte[].class, solidMask);
			}
			if (spec.seedInitialUniformFlow()) {
				float[] initialFlow = (float[]) requestClass
						.getMethod("createFlowState", int.class, int.class, int.class)
						.invoke(null, spec.nx(), spec.ny(), spec.nz());
				requestClass.getMethod(
						"fillUniformFlow",
						float[].class,
						byte[].class,
						float.class,
						float.class,
						float.class,
						float.class
				).invoke(null, initialFlow, solidMask, (float) spec.inletVx(), (float) spec.inletVy(), (float) spec.inletVz(), 0.0f);
				invokeBuilder(builderClass, builder, "initialFlowState", float[].class, initialFlow);
			}
			invokeBuilder(builderClass, builder, "outputFlowAtlas", boolean.class, spec.outputFlowAtlas());
			if (spec.computeForceMoment()) {
				invokeBuilder(builderClass, builder, "forceMomentReference", new Class<?>[] {float.class, float.class, float.class},
						(float) spec.referenceX(), (float) spec.referenceY(), (float) spec.referenceZ());
			} else {
				invokeBuilder(builderClass, builder, "computeForceMoment", boolean.class, false);
			}
			Object request = builderClass.getMethod("build").invoke(builder);
			return L2RequestBuildResult.success(spec, currentCapabilities, request);
		} catch (ReflectiveOperationException | LinkageError error) {
			return L2RequestBuildResult.failure(spec, currentCapabilities,
					"could not build A4MC L2 request: " + error.getClass().getSimpleName());
		}
	}

	static L2Capabilities inspect(ClassLoader loader) {
		if (loader == null) {
			return L2Capabilities.unavailable(true, "class loader is not available");
		}
		try {
			Class<?> windApiClass = Class.forName(WIND_API_CLASS, false, loader);
			Class<?> requestClass = Class.forName(L2_REQUEST_CLASS, false, loader);
			Class<?> resultClass = Class.forName(L2_RESULT_CLASS, false, loader);
			Class<?> forceMomentClass = Class.forName(L2_FORCE_MOMENT_CLASS, false, loader);
			Method runL2 = windApiClass.getMethod("runL2", requestClass);
			Method builder = requestClass.getMethod("builder", int.class, int.class, int.class);
			Class<?> builderClass = builder.getReturnType();

			boolean runL2Available = resultClass.equals(runL2.getReturnType());
			boolean requestBuilderAvailable = builderClass != null
					&& methodExists(builderClass, "cellSizeMeters", float.class)
					&& methodExists(builderClass, "timeStepSeconds", float.class)
					&& methodExists(builderClass, "steps", int.class)
					&& methodExists(builderClass, "sampleStride", int.class)
					&& methodExists(builderClass, "inlet", float.class, float.class, float.class)
					&& methodExists(builderClass, "air", float.class, float.class)
					&& methodExists(builderClass, "outputFlowAtlas", boolean.class)
					&& methodExists(builderClass, "build");
			boolean requestMaskAvailable = methodExists(requestClass, "createSolidMask", int.class, int.class, int.class)
					&& methodExists(requestClass, "createFlowState", int.class, int.class, int.class)
					&& methodExists(requestClass, "cellIndex", int.class, int.class, int.class, int.class, int.class, int.class)
					&& methodExists(requestClass, "fillUniformFlow", float[].class, byte[].class, float.class, float.class, float.class, float.class)
					&& methodExists(builderClass, "solidMask", byte[].class)
					&& methodExists(builderClass, "initialFlowState", float[].class);
			boolean flowAtlasAvailable = methodExists(resultClass, "hasFlowAtlas")
					&& methodExists(resultClass, "flowAtlas")
					&& methodExists(resultClass, "velocityAt", int.class, int.class, int.class)
					&& methodExists(resultClass, "pressureAt", int.class, int.class, int.class)
					&& methodExists(resultClass, "atlasValueCount");
			boolean forceMomentRequestAvailable = methodExists(builderClass, "computeForceMoment", boolean.class)
					&& methodExists(builderClass, "forceMomentReference", float.class, float.class, float.class)
					&& methodExists(requestClass, "computeForceMoment")
					&& methodExists(requestClass, "referenceX")
					&& methodExists(requestClass, "referenceY")
					&& methodExists(requestClass, "referenceZ");
			boolean forceMomentResultAvailable = methodExists(resultClass, "hasForceMoment")
					&& returns(resultClass, "forceMoment", forceMomentClass);
			boolean forceMomentVectorAvailable = methodExists(forceMomentClass, "forceX")
					&& methodExists(forceMomentClass, "forceY")
					&& methodExists(forceMomentClass, "forceZ")
					&& methodExists(forceMomentClass, "momentX")
					&& methodExists(forceMomentClass, "momentY")
					&& methodExists(forceMomentClass, "momentZ")
					&& methodExists(forceMomentClass, "centerOfPressureX")
					&& methodExists(forceMomentClass, "centerOfPressureY")
					&& methodExists(forceMomentClass, "centerOfPressureZ")
					&& methodExists(forceMomentClass, "referenceX")
					&& methodExists(forceMomentClass, "referenceY")
					&& methodExists(forceMomentClass, "referenceZ");
			return new L2Capabilities(
					true,
					true,
					true,
					true,
					true,
					runL2Available,
					requestBuilderAvailable,
					requestMaskAvailable,
					flowAtlasAvailable,
					forceMomentRequestAvailable,
					forceMomentResultAvailable && forceMomentVectorAvailable,
					runL2Available && requestBuilderAvailable,
					runL2Available && requestBuilderAvailable && forceMomentRequestAvailable
							&& forceMomentResultAvailable && forceMomentVectorAvailable,
					"ok"
			);
		} catch (ClassNotFoundException error) {
			return L2Capabilities.unavailable(true, "missing class: " + sanitizeClassName(error.getMessage()));
		} catch (ReflectiveOperationException | LinkageError error) {
			return L2Capabilities.unavailable(true, "incompatible L2 API: " + error.getClass().getSimpleName());
		}
	}

	private static void invokeBuilder(Class<?> owner, Object builder, String name, Class<?> parameterType, Object value)
			throws ReflectiveOperationException {
		invokeBuilder(owner, builder, name, new Class<?>[] {parameterType}, value);
	}

	private static void invokeBuilder(Class<?> owner, Object builder, String name, Class<?>[] parameterTypes, Object... values)
			throws ReflectiveOperationException {
		owner.getMethod(name, parameterTypes).invoke(builder, values);
	}

	private static boolean methodExists(Class<?> owner, String name, Class<?>... parameterTypes) {
		try {
			owner.getMethod(name, parameterTypes);
			return true;
		} catch (NoSuchMethodException error) {
			return false;
		}
	}

	private static boolean returns(Class<?> owner, String name, Class<?> returnType, Class<?>... parameterTypes) {
		try {
			return returnType.equals(owner.getMethod(name, parameterTypes).getReturnType());
		} catch (NoSuchMethodException error) {
			return false;
		}
	}

	private static String sanitizeClassName(String value) {
		if (value == null || value.isBlank()) {
			return "unknown";
		}
		StringBuilder builder = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '$') {
				builder.append(c);
			} else {
				builder.append('_');
			}
		}
		return builder.toString();
	}

	private static String validateSpec(L2RequestSpec spec) {
		if (spec == null) {
			return "L2 request spec must not be null";
		}
		if (!axisInRange(spec.nx()) || !axisInRange(spec.ny()) || !axisInRange(spec.nz())) {
			return "grid axes must be in [" + MIN_GRID_CELLS_PER_AXIS + ", " + MAX_GRID_CELLS_PER_AXIS + "]";
		}
		int cells = cellCount(spec.nx(), spec.ny(), spec.nz());
		if (cells > MAX_GRID_CELLS) {
			return "grid cell count exceeds " + MAX_GRID_CELLS;
		}
		if (!inRange(spec.cellSizeMeters(), MIN_CELL_SIZE_METERS, MAX_CELL_SIZE_METERS)) {
			return "cell size must be finite and in [" + MIN_CELL_SIZE_METERS + ", " + MAX_CELL_SIZE_METERS + "] meters";
		}
		if (!inRange(spec.timeStepSeconds(), MIN_TIME_STEP_SECONDS, MAX_TIME_STEP_SECONDS)) {
			return "time step must be finite and in [" + MIN_TIME_STEP_SECONDS + ", " + MAX_TIME_STEP_SECONDS + "] seconds";
		}
		if (spec.steps() <= 0 || spec.steps() > MAX_SOLVE_STEPS) {
			return "solve steps must be in [1, " + MAX_SOLVE_STEPS + "]";
		}
		if (spec.sampleStride() <= 0 || spec.sampleStride() > MAX_SAMPLE_STRIDE) {
			return "sample stride must be in [1, " + MAX_SAMPLE_STRIDE + "]";
		}
		if (!finiteWithinAbs(spec.inletVx(), MAX_INLET_SPEED_METERS_PER_SECOND)
				|| !finiteWithinAbs(spec.inletVy(), MAX_INLET_SPEED_METERS_PER_SECOND)
				|| !finiteWithinAbs(spec.inletVz(), MAX_INLET_SPEED_METERS_PER_SECOND)) {
			return "inlet velocity components must be finite and <= " + MAX_INLET_SPEED_METERS_PER_SECOND + " m/s";
		}
		if (!inRange(spec.densityKgM3(), MIN_AIR_DENSITY_KG_M3, MAX_AIR_DENSITY_KG_M3)) {
			return "air density must be finite and in [" + MIN_AIR_DENSITY_KG_M3 + ", " + MAX_AIR_DENSITY_KG_M3 + "] kg/m^3";
		}
		if (!inRange(spec.kinematicViscosityM2S(), MIN_AIR_KINEMATIC_VISCOSITY_M2_S, MAX_AIR_KINEMATIC_VISCOSITY_M2_S)) {
			return "air kinematic viscosity must be finite and in [" + MIN_AIR_KINEMATIC_VISCOSITY_M2_S + ", "
					+ MAX_AIR_KINEMATIC_VISCOSITY_M2_S + "] m^2/s";
		}
		if (!Double.isFinite(spec.referenceX()) || !Double.isFinite(spec.referenceY()) || !Double.isFinite(spec.referenceZ())) {
			return "force/moment reference point must be finite";
		}
		byte[] solidMask = spec.solidMask();
		if (solidMask != null && solidMask.length != cells) {
			return "solid mask length must be " + cells;
		}
		return null;
	}

	private static boolean axisInRange(int value) {
		return value >= MIN_GRID_CELLS_PER_AXIS && value <= MAX_GRID_CELLS_PER_AXIS;
	}

	private static boolean inRange(double value, double min, double max) {
		return Double.isFinite(value) && value >= min && value <= max;
	}

	private static boolean finiteWithinAbs(double value, double maxAbs) {
		return Double.isFinite(value) && Math.abs(value) <= maxAbs;
	}

	private static int cellCount(int nx, int ny, int nz) {
		return Math.multiplyExact(Math.multiplyExact(nx, ny), nz);
	}

	private static int defaultSampleStride(int nx, int ny, int nz) {
		return Math.max(1, Math.min(Math.min(nx, ny), nz) / 16);
	}

	public record L2RequestSpec(
			int nx,
			int ny,
			int nz,
			double cellSizeMeters,
			double timeStepSeconds,
			int steps,
			int sampleStride,
			double inletVx,
			double inletVy,
			double inletVz,
			double densityKgM3,
			double kinematicViscosityM2S,
			boolean outputFlowAtlas,
			boolean computeForceMoment,
			double referenceX,
			double referenceY,
			double referenceZ,
			byte[] solidMask,
			boolean seedInitialUniformFlow
	) {
		public L2RequestSpec {
			solidMask = solidMask == null ? null : solidMask.clone();
		}

		public static L2RequestSpec forceMomentProbe(
				int nx,
				int ny,
				int nz,
				double cellSizeMeters,
				int steps,
				double inletVx,
				double inletVy,
				double inletVz,
				byte[] solidMask
		) {
			return new L2RequestSpec(
					nx,
					ny,
					nz,
					cellSizeMeters,
					DEFAULT_TIME_STEP_SECONDS,
					steps,
					defaultSampleStride(nx, ny, nz),
					inletVx,
					inletVy,
					inletVz,
					DEFAULT_AIR_DENSITY_KG_M3,
					DEFAULT_AIR_KINEMATIC_VISCOSITY_M2_S,
					false,
					true,
					0.5 * nx * cellSizeMeters,
					0.5 * ny * cellSizeMeters,
					0.5 * nz * cellSizeMeters,
					solidMask,
					true
			);
		}

		@Override
		public byte[] solidMask() {
			return solidMask == null ? null : solidMask.clone();
		}
	}

	public record L2RequestBuildResult(
			boolean built,
			L2RequestSpec spec,
			L2Capabilities capabilities,
			Object request,
			String message
	) {
		private static L2RequestBuildResult success(L2RequestSpec spec, L2Capabilities capabilities, Object request) {
			return new L2RequestBuildResult(true, spec, capabilities, request, "ok");
		}

		private static L2RequestBuildResult failure(L2RequestSpec spec, L2Capabilities capabilities, String message) {
			return new L2RequestBuildResult(
					false,
					spec,
					capabilities == null ? L2Capabilities.notInspected() : capabilities,
					null,
					message == null || message.isBlank() ? "unavailable" : message
			);
		}
	}

	public record L2Capabilities(
			boolean modLoaded,
			boolean windApiAvailable,
			boolean requestAvailable,
			boolean resultAvailable,
			boolean forceMomentClassAvailable,
			boolean runL2Available,
			boolean requestBuilderAvailable,
			boolean requestMaskAvailable,
			boolean flowAtlasAvailable,
			boolean forceMomentRequestAvailable,
			boolean forceMomentResultAvailable,
			boolean available,
			boolean forceMomentAvailable,
			String message
	) {
		private static L2Capabilities notInspected() {
			return unavailable(true, "not inspected");
		}

		private static L2Capabilities unavailable(boolean modLoaded, String message) {
			return new L2Capabilities(
					modLoaded,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					false,
					message == null || message.isBlank() ? "unavailable" : message
			);
		}
	}
}
