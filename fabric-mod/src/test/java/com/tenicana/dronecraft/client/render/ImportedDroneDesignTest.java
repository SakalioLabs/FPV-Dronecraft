package com.tenicana.dronecraft.client.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.tenicana.dronecraft.camera.FpvCameraOrientation;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class ImportedDroneDesignTest {
	private static final float EPSILON = 2.0e-5f;

	@Test
	void bakedQuadPreservesEverySuppliedCubeInflationRotationAndFaceUv() throws Exception {
		JsonObject fixture = sourceFixture();
		ModelPart root = DroneEntityModel.createBodyLayer().bakeRoot();
		DroneEntityModel model = new DroneEntityModel(root);
		model.setupAnim(sourceLayout());
		Map<String, JsonObject> sourceCubes = new HashMap<>();
		for (var element : fixture.getAsJsonArray("elements")) {
			JsonObject cube = element.getAsJsonObject();
			assertFalse(sourceCubes.containsKey(cube.get("name").getAsString()), "Source cube names must be unique");
			sourceCubes.put(cube.get("name").getAsString(), cube);
		}
		int[] count = {0};
		root.visit(rendererPose(), (pose, path, index, bakedCube) -> {
			if (isExtraAssembly(path)) {
				return;
			}
			String name = path.substring(path.lastIndexOf('/') + 1);
			JsonObject source = sourceCubes.remove(name);
			assertNotNull(source, "Unrecognized or duplicated imported cube " + path);
			count[0]++;
			Vector3f origin = vector(source.getAsJsonArray("origin"));
			Vector3f rotationDegrees = vector(source.getAsJsonArray("rotation"));
			Quaternionf rotation = new Quaternionf().rotationZYX(
					(float) Math.toRadians(rotationDegrees.z), (float) Math.toRadians(rotationDegrees.y),
					(float) Math.toRadians(rotationDegrees.x));
			Vector3f from = vector(source.getAsJsonArray("from"));
			Vector3f to = vector(source.getAsJsonArray("to"));
			float inflate = source.get("inflate").getAsFloat();
			Vector3f[] corners = new Vector3f[8];
			for (int corner = 0; corner < 8; corner++) {
				Vector3f point = new Vector3f(
						(corner & 1) == 0 ? from.x - inflate : to.x + inflate,
						(corner & 2) == 0 ? from.y - inflate : to.y + inflate,
						(corner & 4) == 0 ? from.z - inflate : to.z + inflate);
				point.sub(origin).rotate(rotation).add(origin);
				corners[corner] = new Vector3f(-point.x / 16.0f, point.y / 16.0f + 0.001f, -point.z / 16.0f);
			}
			for (ModelPart.Polygon polygon : bakedCube.polygons) {
				Vector3f worldNormal = pose.normal().transform(new Vector3f(polygon.normal())).normalize();
				String faceName = sourceFaceForNormal(worldNormal, rotation);
				JsonArray uv = source.getAsJsonObject("faces").getAsJsonObject(faceName).getAsJsonArray("uv");
				float minU = Math.min(uv.get(0).getAsFloat(), uv.get(2).getAsFloat()) / 512.0f;
				float maxU = Math.max(uv.get(0).getAsFloat(), uv.get(2).getAsFloat()) / 512.0f;
				float minV = Math.min(uv.get(1).getAsFloat(), uv.get(3).getAsFloat()) / 512.0f;
				float maxV = Math.max(uv.get(1).getAsFloat(), uv.get(3).getAsFloat()) / 512.0f;
				float actualMinU = Float.POSITIVE_INFINITY;
				float actualMaxU = Float.NEGATIVE_INFINITY;
				float actualMinV = Float.POSITIVE_INFINITY;
				float actualMaxV = Float.NEGATIVE_INFINITY;
				for (ModelPart.Vertex vertex : polygon.vertices()) {
					Vector3f world = pose.pose().transformPosition(new Vector3f(vertex.worldX(), vertex.worldY(), vertex.worldZ()));
					float closest = Float.POSITIVE_INFINITY;
					for (Vector3f corner : corners) {
						closest = Math.min(closest, world.distance(corner));
					}
					assertTrue(closest < EPSILON, name + " has incorrect exported corner " + world);
					actualMinU = Math.min(actualMinU, vertex.u());
					actualMaxU = Math.max(actualMaxU, vertex.u());
					actualMinV = Math.min(actualMinV, vertex.v());
					actualMaxV = Math.max(actualMaxV, vertex.v());
				}
				assertEquals(minU, actualMinU, EPSILON, name + '/' + faceName + " atlas min U");
				assertEquals(maxU, actualMaxU, EPSILON, name + '/' + faceName + " atlas max U");
				assertEquals(minV, actualMinV, EPSILON, name + '/' + faceName + " atlas min V");
				assertEquals(maxV, actualMaxV, EPSILON, name + '/' + faceName + " atlas max V");
			}
		});
		assertEquals(280, count[0]);
		assertTrue(sourceCubes.isEmpty(), "Some supplied model cubes are absent from the rendered quad");
		assertEquals(fixture.get("source_sha256").getAsString(), ImportedDroneDesign.SOURCE_SHA256);
	}

	@Test
	void suppliedTextureIsByteExactAndUsesItsLogicalUvResolution() throws Exception {
		try (InputStream input = getClass().getResourceAsStream("/assets/fpvdrone/textures/entity/drone.png")) {
			assertNotNull(input);
			byte[] bytes = input.readAllBytes();
			assertEquals(ImportedDroneDesign.TEXTURE_SHA256,
					HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
			try (InputStream pixels = new java.io.ByteArrayInputStream(bytes)) {
				var image = ImageIO.read(pixels);
				assertEquals(2048, image.getWidth());
				assertEquals(2048, image.getHeight());
			}
		}
		assertEquals(512, ImportedDroneDesign.TEXTURE_WIDTH);
		assertEquals(512, ImportedDroneDesign.TEXTURE_HEIGHT);
	}

	@Test
	void nestedBodyAxesAgreeWithFpvCameraAcrossCombinedSignedTurns() {
		ModelPart root = DroneEntityModel.createBodyLayer().bakeRoot();
		DroneEntityModel model = new DroneEntityModel(root);
		DroneEntityRenderState state = sourceLayout();
		ModelPart body = root.getChild("body");
		ModelPart pitch = body.getChild("pitch");
		ModelPart roll = pitch.getChild("roll");
		for (float yawDegrees : new float[] {-170, 0, 37, 490}) {
			for (float pitchDegrees : new float[] {-735, -330, -90, 0, 95, 390, 735}) {
				for (float rollDegrees : new float[] {-745, -30, 0, 90, 375, 745}) {
					state.yawRadians = (float) Math.toRadians(yawDegrees);
					state.pitchRadians = (float) Math.toRadians(pitchDegrees);
					state.rollRadians = (float) Math.toRadians(rollDegrees);
					model.setupAnim(state);
					PoseStack pose = rendererPose();
					body.translateAndRotate(pose);
					pitch.translateAndRotate(pose);
					roll.translateAndRotate(pose);
					Vector3f forwards = pose.last().pose().transformDirection(new Vector3f(0, 0, 1)).normalize();
					Vector3f up = pose.last().pose().transformDirection(new Vector3f(0, -1, 0)).normalize();
					var camera = FpvCameraOrientation.fromCameraAngles(yawDegrees, pitchDegrees, state.rollRadians);
					assertTrue(forwards.distance(camera.forwards()) < EPSILON, "Model nose differs from the control frame");
					assertTrue(up.distance(camera.up()) < EPSILON, "Model top differs from the control frame");
				}
			}
		}
	}

	@Test
	void asymmetricEightRotorLayoutKeepsMotorGuardAndPropellerCentersAlignedAndBeamsAnchored() {
		ModelPart root = DroneEntityModel.createBodyLayer().bakeRoot();
		DroneEntityModel model = new DroneEntityModel(root);
		DroneEntityRenderState state = sourceLayout();
		state.rotorCount = 8;
		state.ageInTicks = 1;
		for (int index = 0; index < 8; index++) {
			state.rotorXModelUnits[index] = (float) Math.sin(index * Math.PI / 4.0 + 0.3) * (15 + index);
			state.rotorZModelUnits[index] = (float) Math.cos(index * Math.PI / 4.0 + 0.3) * (15 + index);
			state.rotorYModelUnits[index] = index % 2 == 0 ? 3 : -2;
			state.rotorRpm[index] = 300;
			state.rotorSpinDirection[index] = index % 2 == 0 ? 1 : -1;
		}
		model.setupAnim(state);
		ModelPart design = design(root);
		for (int index = 0; index < 8; index++) {
			ModelPart assembly = design.getChild("assembly_" + index);
			ModelPart rotor = design.getChild("rotor_" + index);
			ModelPart guard = assembly.getChild("guard_" + (index & 3));
			assertTrue(assembly.visible && rotor.visible);
			PoseStack base = attitudePose(root);
			design.translateAndRotate(base);
			Vector3f rotorCenter = partPosition(base, rotor);
			Vector3f expected = new Vector3f(state.rotorXModelUnits[index] / 16.0f,
					(4.9f + state.rotorYModelUnits[index]) / 16.0f + 0.001f,
					state.rotorZModelUnits[index] / 16.0f);
			assertTrue(rotorCenter.distance(expected) < EPSILON, "Cached physical rotor offsets were mirrored");
			assembly.translateAndRotate(base);
			assertTrue(partPosition(base, guard).distance(rotorCenter) < EPSILON, "Guard and blade centers differ");
			ModelPart beam = assembly.getChild("arm_" + (index & 3) + "_carbon_beam");
			Vector3f beamRoot = partPosition(base, beam);
			assertEquals(0, beamRoot.x, EPSILON);
			assertEquals(0, beamRoot.z, EPSILON);
			assertEquals(state.rotorSpinDirection[index] * (float) (Math.PI / 2.0), rotor.yRot, EPSILON);
			assertEquals(1, guard.xScale);
			assertEquals(1, guard.yScale);
			assertEquals(1, guard.zScale);
		}
		state.rotorCount = 4;
		model.setupAnim(state);
		for (int index = 4; index < 8; index++) {
			assertFalse(design.getChild("assembly_" + index).visible);
			assertFalse(design.getChild("rotor_" + index).visible);
		}
	}

	private static String sourceFaceForNormal(Vector3f worldNormal, Quaternionf rotation) {
		String[] names = {"east", "west", "up", "down", "south", "north"};
		Vector3f[] normals = {new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0), new Vector3f(0, 1, 0),
				new Vector3f(0, -1, 0), new Vector3f(0, 0, 1), new Vector3f(0, 0, -1)};
		for (int index = 0; index < normals.length; index++) {
			Vector3f normal = normals[index].rotate(rotation);
			normal.set(-normal.x, normal.y, -normal.z);
			if (normal.dot(worldNormal) > 0.9999f) {
				return names[index];
			}
		}
		throw new AssertionError("Invalid imported face orientation: " + worldNormal);
	}

	private static boolean isExtraAssembly(String path) {
		for (int index = 4; index < 8; index++) {
			if (path.contains("/assembly_" + index + "/") || path.contains("/rotor_" + index + "/")) {
				return true;
			}
		}
		return false;
	}

	private static JsonObject sourceFixture() throws Exception {
		try (InputStream input = ImportedDroneDesignTest.class.getResourceAsStream("drone-design-v1.json")) {
			assertNotNull(input);
			return JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static DroneEntityRenderState sourceLayout() {
		DroneEntityRenderState state = new DroneEntityRenderState();
		for (int index = 0; index < 4; index++) {
			state.rotorXModelUnits[index] = ImportedDroneDesign.ARM_X[index];
			state.rotorZModelUnits[index] = -ImportedDroneDesign.ARM_Z[index];
			state.rotorSpinDirection[index] = 1;
		}
		return state;
	}

	private static Vector3f vector(JsonArray coordinates) {
		return new Vector3f(coordinates.get(0).getAsFloat(), coordinates.get(1).getAsFloat(), coordinates.get(2).getAsFloat());
	}

	private static PoseStack rendererPose() {
		PoseStack stack = new PoseStack();
		stack.scale(-1, -1, 1);
		stack.translate(0, -1.501f, 0);
		return stack;
	}

	private static PoseStack attitudePose(ModelPart root) {
		PoseStack stack = rendererPose();
		ModelPart body = root.getChild("body");
		body.translateAndRotate(stack);
		ModelPart pitch = body.getChild("pitch");
		pitch.translateAndRotate(stack);
		pitch.getChild("roll").translateAndRotate(stack);
		return stack;
	}

	private static ModelPart design(ModelPart root) {
		return root.getChild("body").getChild("pitch").getChild("roll").getChild("design");
	}

	private static Vector3f partPosition(PoseStack stack, ModelPart part) {
		stack.pushPose();
		part.translateAndRotate(stack);
		Vector3f position = stack.last().pose().transformPosition(new Vector3f());
		stack.popPose();
		return position;
	}
}
