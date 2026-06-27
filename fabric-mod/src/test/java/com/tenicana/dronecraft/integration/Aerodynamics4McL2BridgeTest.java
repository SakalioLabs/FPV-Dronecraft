package com.tenicana.dronecraft.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

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
		assertTrue(source.contains("methodExists(requestClass, \"createSolidMask\", int.class, int.class, int.class)"),
				"probe should verify solid-mask creation");
		assertTrue(source.contains("methodExists(requestClass, \"createFlowState\", int.class, int.class, int.class)"),
				"probe should verify flow-state creation");
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
