package com.tenicana.dronecraft.integration;

import java.lang.reflect.Method;

import net.fabricmc.loader.api.FabricLoader;

public final class Aerodynamics4McL2Bridge {
	private static final String MOD_ID = "aerodynamics4mc";
	private static final String WIND_API_CLASS = "com.aerodynamics4mc.api.AeroWindApi";
	private static final String L2_REQUEST_CLASS = "com.aerodynamics4mc.api.AeroL2Request";
	private static final String L2_RESULT_CLASS = "com.aerodynamics4mc.api.AeroL2Result";
	private static final String L2_FORCE_MOMENT_CLASS = "com.aerodynamics4mc.api.AeroL2ForceMoment";
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
					&& methodExists(builderClass, "build");
			boolean requestMaskAvailable = methodExists(requestClass, "createSolidMask", int.class, int.class, int.class)
					&& methodExists(requestClass, "createFlowState", int.class, int.class, int.class)
					&& methodExists(requestClass, "cellIndex", int.class, int.class, int.class, int.class, int.class, int.class)
					&& methodExists(requestClass, "fillUniformFlow", float[].class, byte[].class, float.class, float.class, float.class, float.class);
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
