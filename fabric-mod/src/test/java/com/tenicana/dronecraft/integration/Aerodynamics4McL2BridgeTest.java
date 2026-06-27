package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.aerodynamics4mc.api.AeroL2Request;

class Aerodynamics4McL2BridgeTest {
	@Test
	void capabilityProbeIsSafeWhenA4mcClassesAreAbsent() {
		ClassLoader emptyLoader = new ClassLoader(null) {
		};

		Aerodynamics4McL2Bridge.L2Capabilities capabilities = Aerodynamics4McL2Bridge.inspect(emptyLoader);

		assertTrue(capabilities.modLoaded(), "probe mode treats the mod as intentionally present");
		assertFalse(capabilities.available(), "L2 bridge should be unavailable without the A4MC API classes");
		assertFalse(capabilities.forceMomentAvailable(), "force/moment should be unavailable without the L2 API classes");
		assertTrue(capabilities.message().startsWith("missing class:"),
				"missing API classes should be reported without throwing");
	}

	@Test
	void capabilityProbeAcceptsA4mcL2ContractWhenClassesArePresent() {
		Aerodynamics4McL2Bridge.L2Capabilities capabilities =
				Aerodynamics4McL2Bridge.inspect(getClass().getClassLoader());

		assertTrue(capabilities.available(), capabilities.message());
		assertTrue(capabilities.runL2Available());
		assertTrue(capabilities.requestBuilderAvailable());
		assertTrue(capabilities.requestMaskAvailable());
		assertTrue(capabilities.flowAtlasAvailable());
		assertTrue(capabilities.forceMomentAvailable());
	}

	@Test
	void buildRequestCreatesBoundedForceMomentProbeThroughReflectedA4mcApi() {
		byte[] solidMask = new byte[16 * 12 * 8];
		solidMask[17] = 1;
		Aerodynamics4McL2Bridge.L2RequestSpec spec =
				Aerodynamics4McL2Bridge.L2RequestSpec.forceMomentProbe(16, 12, 8, 0.25, 40, 12.0, 0.5, -1.0, solidMask);

		Aerodynamics4McL2Bridge.L2RequestBuildResult result =
				Aerodynamics4McL2Bridge.buildRequest(getClass().getClassLoader(), spec);

		assertTrue(result.built(), result.message());
		assertTrue(result.request() instanceof AeroL2Request);
		AeroL2Request request = (AeroL2Request) result.request();
		assertEquals(16, request.nx());
		assertEquals(12, request.ny());
		assertEquals(8, request.nz());
		assertEquals(0.25f, request.dxMeters(), 1.0e-6f);
		assertEquals(0.05f, request.dtSeconds(), 1.0e-6f);
		assertEquals(40, request.steps());
		assertEquals(1, request.sampleStride());
		assertEquals(12.0f, request.inletVx(), 1.0e-6f);
		assertEquals(0.5f, request.inletVy(), 1.0e-6f);
		assertEquals(-1.0f, request.inletVz(), 1.0e-6f);
		assertEquals(1.225f, request.densityKgM3(), 1.0e-6f);
		assertEquals(1.5e-5f, request.kinematicViscosityM2S(), 1.0e-9f);
		assertFalse(request.outputFlowAtlas(), "force/moment probe should not request a full atlas by default");
		assertTrue(request.computeForceMoment());
		assertEquals(2.0f, request.referenceX(), 1.0e-6f);
		assertEquals(1.5f, request.referenceY(), 1.0e-6f);
		assertEquals(1.0f, request.referenceZ(), 1.0e-6f);
		assertArrayEquals(solidMask, request.solidMask());
		assertTrue(request.hasInitialFlowState());
		assertEquals(0.0f, request.initialFlowState()[17 * 4], 1.0e-6f,
				"solid cells should be seeded with zero velocity");
		assertEquals(12.0f, request.initialFlowState()[18 * 4], 1.0e-6f,
				"open cells should be seeded with inlet velocity");
	}

	@Test
	void buildRequestRejectsUnsafeSpecsBeforeReflection() {
		Aerodynamics4McL2Bridge.L2RequestSpec oversized =
				Aerodynamics4McL2Bridge.L2RequestSpec.forceMomentProbe(256, 16, 16, 0.25, 40, 12.0, 0.0, 0.0, null);

		Aerodynamics4McL2Bridge.L2RequestBuildResult result =
				Aerodynamics4McL2Bridge.buildRequest(getClass().getClassLoader(), oversized);

		assertFalse(result.built());
		assertTrue(result.message().contains("grid axes"), result.message());
	}

	@Test
	void runRequestInvokesA4mcAndExtractsBoundedForceMomentSummary() {
		Aerodynamics4McL2Bridge.L2RequestSpec spec =
				Aerodynamics4McL2Bridge.L2RequestSpec.forceMomentProbe(16, 16, 16, 0.20, 24, 8.0, 0.0, 0.5, null);

		Aerodynamics4McL2Bridge.L2RunResult result =
				Aerodynamics4McL2Bridge.run(getClass().getClassLoader(), spec);

		assertTrue(result.invoked(), result.message());
		assertTrue(result.succeeded(), result.status());
		assertTrue(result.available(), result.status());
		assertTrue(result.buildResult().built(), result.buildResult().message());
		assertEquals("OK", result.status());
		assertEquals("", result.message());
		assertEquals("test-runtime", result.runtimeInfo());
		assertFalse(result.hasFlowAtlas(), "force/moment probes should avoid full flow atlas output by default");
		assertEquals(0, result.atlasValueCount());
		assertTrue(result.hasForceMoment());
		Aerodynamics4McL2Bridge.L2ForceMomentSample forceMoment = result.forceMoment();
		assertEquals(1.25, forceMoment.forceX(), 1.0e-6);
		assertEquals(-0.50, forceMoment.forceY(), 1.0e-6);
		assertEquals(3.00, forceMoment.forceZ(), 1.0e-6);
		assertEquals(0.20, forceMoment.momentX(), 1.0e-6);
		assertEquals(-0.10, forceMoment.momentY(), 1.0e-6);
		assertEquals(0.40, forceMoment.momentZ(), 1.0e-6);
		assertEquals(Math.sqrt(1.25 * 1.25 + 0.50 * 0.50 + 3.00 * 3.00), forceMoment.forceMagnitudeN(), 1.0e-9);
		assertEquals(Math.sqrt(0.20 * 0.20 + 0.10 * 0.10 + 0.40 * 0.40), forceMoment.momentMagnitudeNm(), 1.0e-6);
		assertEquals(Math.sqrt(0.10 * 0.10 + 0.20 * 0.20 + 0.30 * 0.30), forceMoment.centerOfPressureOffsetMeters(), 1.0e-6);
	}

	@Test
	void runRequestDoesNotInvokeA4mcWhenSpecIsRejected() {
		Aerodynamics4McL2Bridge.L2RequestSpec unsafe =
				Aerodynamics4McL2Bridge.L2RequestSpec.forceMomentProbe(16, 16, 16, 0.20, 24, 120.0, 0.0, 0.0, null);

		Aerodynamics4McL2Bridge.L2RunResult result =
				Aerodynamics4McL2Bridge.run(getClass().getClassLoader(), unsafe);

		assertFalse(result.invoked());
		assertFalse(result.succeeded());
		assertFalse(result.available());
		assertFalse(result.hasForceMoment());
		assertTrue(result.message().contains("inlet velocity"), result.message());
	}

	@Test
	void capabilityProbeTracksA4mcL2WindTunnelContract() throws IOException {
		String source = Files.readString(l2BridgeSource(), StandardCharsets.UTF_8);

		assertTrue(source.contains("\"com.aerodynamics4mc.api.AeroWindApi\""),
				"probe should bind the API-level wind tunnel entry point");
		assertTrue(source.contains("getMethod(\"runL2\", requestClass)"),
				"probe should verify the L2 solve entry point");
		assertTrue(source.contains("\"com.aerodynamics4mc.api.AeroL2Request\""),
				"probe should bind the L2 request type");
		assertTrue(source.contains("getMethod(\"builder\", int.class, int.class, int.class)"),
				"probe should verify grid request construction");
		assertTrue(source.contains("methodExists(builderClass, \"cellSizeMeters\", float.class)"),
				"probe should verify cell-size binding");
		assertTrue(source.contains("methodExists(builderClass, \"timeStepSeconds\", float.class)"),
				"probe should verify timestep binding");
		assertTrue(source.contains("methodExists(builderClass, \"inlet\", float.class, float.class, float.class)"),
				"probe should verify inlet velocity binding");
		assertTrue(source.contains("methodExists(builderClass, \"air\", float.class, float.class)"),
				"probe should verify density/viscosity binding");
		assertTrue(source.contains("methodExists(builderClass, \"outputFlowAtlas\", boolean.class)"),
				"probe should verify flow-atlas request control");
		assertTrue(source.contains("methodExists(requestClass, \"createSolidMask\", int.class, int.class, int.class)"),
				"probe should verify solid-mask creation");
		assertTrue(source.contains("methodExists(requestClass, \"createFlowState\", int.class, int.class, int.class)"),
				"probe should verify flow-state creation");
		assertTrue(source.contains("methodExists(builderClass, \"solidMask\", byte[].class)"),
				"probe should verify solid-mask builder binding");
		assertTrue(source.contains("methodExists(builderClass, \"initialFlowState\", float[].class)"),
				"probe should verify initial-flow builder binding");
		assertTrue(source.contains("methodExists(requestClass, \"fillUniformFlow\", float[].class, byte[].class"),
				"probe should verify uniform initial flow population");
		assertTrue(source.contains("\"com.aerodynamics4mc.api.AeroL2Result\""),
				"probe should bind the L2 result type");
		assertTrue(source.contains("methodExists(resultClass, \"hasFlowAtlas\")"),
				"probe should verify flow-atlas result availability");
		assertTrue(source.contains("methodExists(resultClass, \"velocityAt\", int.class, int.class, int.class)"),
				"probe should verify atlas velocity sampling");
		assertTrue(source.contains("methodExists(resultClass, \"pressureAt\", int.class, int.class, int.class)"),
				"probe should verify atlas pressure sampling");
		assertTrue(source.contains("\"com.aerodynamics4mc.api.AeroL2ForceMoment\""),
				"probe should bind the L2 force/moment type");
		assertTrue(source.contains("methodExists(builderClass, \"computeForceMoment\", boolean.class)"),
				"probe should verify force/moment request activation");
		assertTrue(source.contains("methodExists(builderClass, \"forceMomentReference\", float.class, float.class, float.class)"),
				"probe should verify force/moment reference point binding");
		assertTrue(source.contains("returns(resultClass, \"forceMoment\", forceMomentClass)"),
				"probe should verify force/moment result binding");
		assertTrue(source.contains("methodExists(forceMomentClass, \"forceX\")"),
				"probe should verify force vector components");
		assertTrue(source.contains("methodExists(forceMomentClass, \"momentZ\")"),
				"probe should verify moment vector components");
		assertTrue(source.contains("methodExists(forceMomentClass, \"centerOfPressureX\")"),
				"probe should verify pressure-center components");
		assertTrue(source.contains("methodExists(forceMomentClass, \"referenceZ\")"),
				"probe should verify force/moment reference components");
	}

	private static Path l2BridgeSource() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null) {
			Path direct = current.resolve("src/main/java/com/tenicana/dronecraft/integration/Aerodynamics4McL2Bridge.java");
			if (Files.exists(direct)) {
				return direct;
			}
			Path child = current.resolve("fabric-mod/src/main/java/com/tenicana/dronecraft/integration/Aerodynamics4McL2Bridge.java");
			if (Files.exists(child)) {
				return child;
			}
			current = current.getParent();
		}
		fail("Cannot locate Aerodynamics4McL2Bridge.java");
		return Path.of(".");
	}
}
