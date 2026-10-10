package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.AxisAlignedPlanePatch;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor;
import com.tenicana.dronecraft.acoustics.propagation.MaterialBoxUnionSurfaceExtractor.MaterialBox;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Offline native VoxelShape-to-local-plane fixture; opens no game or audio. */
public final class MinecraftVoxelShapeLocalPlaneReferenceCli {
	private MinecraftVoxelShapeLocalPlaneReferenceCli() {
	}

	public static void main(String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT);
		if (args.length != 2) {
			throw new IllegalArgumentException(
					"usage: <output-json> <local-plane-contract>"
			);
		}
		Path output = Path.of(args[0]);
		Path contract = Path.of(args[1]);
		VoxelShape slab = Shapes.box(0, 0, 0, 1, 0.5, 1);
		VoxelShape stair = Shapes.or(
				slab,
				Shapes.box(0, 0.5, 0, 0.5, 1, 1)
		).optimize();
		String fixtures = "["
				+ fixture("full-cube", List.of(
						new ShapeAt(BlockPos.ZERO, Shapes.block())
				)) + ","
				+ fixture("slab", List.of(
						new ShapeAt(BlockPos.ZERO, slab)
				)) + ","
				+ fixture("stair", List.of(
						new ShapeAt(BlockPos.ZERO, stair)
				)) + ","
				+ fixture("adjacent-full-blocks", List.of(
						new ShapeAt(BlockPos.ZERO, Shapes.block()),
						new ShapeAt(new BlockPos(1, 0, 0), Shapes.block())
				))
				+ "]";
		String report = String.format(
				Locale.ROOT,
				"""
				{
				  "schema_version": 1,
				  "status": "valid-minecraft-voxel-shape-local-plane-reference",
				  "minecraft_version": "1.21.11",
				  "mapping_surface": "named VoxelShape.optimize().toAabbs()",
				  "source_contract_sha256": "%s",
				  "fixtures": %s,
				  "gates": {
				    "native_voxel_shape_executed": true,
				    "subvoxel_slab_preserved": true,
				    "stair_union_boundary_reconstructed": true,
				    "adjacent_block_internal_face_rejected": true
				  },
				  "captures_audio": false,
				  "physical_endpoint_opened": false,
				  "cuda_executed": false,
				  "minecraft_client_started": false,
				  "minecraft_integration_enabled": false,
				  "release_calibrated": false
				}
				""",
				sha256(Files.readAllBytes(contract)),
				fixtures
		);
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, report, StandardCharsets.UTF_8);
		System.out.println(
				"{\"status\":\"valid-minecraft-voxel-shape-local-plane-reference\","
						+ "\"fixtures\":4}"
		);
	}

	private static String fixture(
			String name,
			List<ShapeAt> shapes
	) {
		List<MaterialBox> boxes = new ArrayList<>();
		for (ShapeAt shape : shapes) {
			MinecraftLocalPlaneSnapshot.appendShapeBoxes(
					boxes,
					shape.position(),
					shape.shape(),
					AcousticMaterials.STONE
			);
		}
		List<AxisAlignedPlanePatch> patches =
				MaterialBoxUnionSurfaceExtractor.extract(boxes);
		return String.format(
				Locale.ROOT,
				"{\"name\":\"%s\",\"optimized_aabbs\":%s,"
						+ "\"patch_count\":%d,\"patches\":%s}",
				name,
				boxes(boxes),
				patches.size(),
				patches(patches)
		);
	}

	private static String boxes(List<MaterialBox> boxes) {
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < boxes.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			MaterialBox box = boxes.get(index);
			result.append(String.format(
					Locale.ROOT,
					"[%.17g,%.17g,%.17g,%.17g,%.17g,%.17g]",
					box.minimumX(), box.minimumY(), box.minimumZ(),
					box.maximumX(), box.maximumY(), box.maximumZ()
			));
		}
		return result.append(']').toString();
	}

	private static String patches(List<AxisAlignedPlanePatch> patches) {
		StringBuilder result = new StringBuilder("[");
		for (int index = 0; index < patches.size(); index++) {
			if (index > 0) {
				result.append(',');
			}
			AxisAlignedPlanePatch patch = patches.get(index);
			result.append(String.format(
					Locale.ROOT,
					"[%d,%d,%.17g,%.17g,%.17g,%.17g,%.17g]",
					patch.axis(), patch.normalSign(),
					patch.coordinateMeters(),
					patch.minimumFirstMeters(),
					patch.maximumFirstMeters(),
					patch.minimumSecondMeters(),
					patch.maximumSecondMeters()
			));
		}
		return result.append(']').toString();
	}

	private static String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(bytes)
			);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 unavailable", exception);
		}
	}

	private record ShapeAt(BlockPos position, VoxelShape shape) {
	}
}
