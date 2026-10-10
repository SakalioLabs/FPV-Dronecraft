package com.tenicana.dronecraft.client.sound;

import com.tenicana.dronecraft.acoustics.AcousticMaterial;
import com.tenicana.dronecraft.acoustics.AcousticMaterials;
import com.tenicana.dronecraft.acoustics.propagation.DirectPathSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class MinecraftAcousticMaterials {
	/**
	 * Version of the BlockState/tag/SoundType/fluid/collision mapping rules.
	 * Increment whenever classification or fill-fraction behavior changes.
	 */
	static final int MAPPING_ALGORITHM_VERSION = 1;

	private MinecraftAcousticMaterials() {
	}

	static String materialTableSha256() {
		return AcousticMaterials.diagnosticSha256();
	}

	/**
	 * Offline fingerprint of the currently loaded registry/tag/SoundType
	 * classification environment. Per-state collision, fluid, and fill data
	 * remain covered by the captured snapshot hash.
	 */
	static String diagnosticContentFingerprintSha256() {
		List<ClassificationEntry> entries = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK) {
			String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
			for (BlockState state : block.getStateDefinition()
					.getPossibleStates()) {
				entries.add(new ClassificationEntry(
						blockId,
						state.toString(),
						classify(state).id()
				));
			}
		}
		entries.sort(
				Comparator.comparing(ClassificationEntry::blockId)
						.thenComparing(ClassificationEntry::stateDescriptor)
		);
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (DataOutputStream output = new DataOutputStream(bytes)) {
				output.write(
						"MCFMAP01".getBytes(StandardCharsets.US_ASCII)
				);
				output.writeInt(MAPPING_ALGORITHM_VERSION);
				output.write(HexFormat.of().parseHex(
						materialTableSha256()
				));
				output.writeInt(entries.size());
				for (ClassificationEntry entry : entries) {
					writeString(output, entry.blockId());
					writeString(output, entry.stateDescriptor());
					writeString(output, entry.materialId());
				}
			}
			return HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256")
							.digest(bytes.toByteArray())
			);
		} catch (IOException error) {
			throw new IllegalStateException(
					"unexpected in-memory mapping fingerprint I/O failure",
					error
			);
		} catch (NoSuchAlgorithmException error) {
			throw new IllegalStateException("SHA-256 is unavailable", error);
		}
	}

	static DirectPathSolver.MaterialSample sample(
			BlockGetter level,
			BlockPos position,
			BlockState state
	) {
		if (state.isAir()) {
			return DirectPathSolver.MaterialSample.AIR;
		}

		VoxelShape collision = state.getCollisionShape(level, position);
		double fillFraction = collisionFill(collision);
		if (fillFraction <= 1.0e-6 && !state.getFluidState().isEmpty()) {
			return DirectPathSolver.MaterialSample.full(AcousticMaterials.WATER);
		}

		AcousticMaterial material = classify(state);
		if (state.is(BlockTags.LEAVES)) {
			fillFraction = Math.max(fillFraction, 0.35);
		} else if (fillFraction <= 1.0e-6) {
			fillFraction = isPorous(material) ? 0.15 : 0.05;
		}
		return new DirectPathSolver.MaterialSample(material, Math.min(1.0, fillFraction));
	}

	private static AcousticMaterial classify(BlockState state) {
		if (state.is(BlockTags.LEAVES)) {
			return AcousticMaterials.FOLIAGE;
		}
		if (state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS)
				|| state.is(BlockTags.DAMPENS_VIBRATIONS)) {
			return AcousticMaterials.SOFT;
		}
		SoundType sound = state.getSoundType();
		if (sound == SoundType.GLASS) {
			return AcousticMaterials.GLASS;
		}
		if (isMetal(sound)) {
			return AcousticMaterials.METAL;
		}
		if (isWood(sound)) {
			return AcousticMaterials.WOOD;
		}
		if (isPorousSound(sound)) {
			return AcousticMaterials.FOLIAGE;
		}
		return AcousticMaterials.STONE;
	}

	private static boolean isMetal(SoundType sound) {
		return sound == SoundType.METAL
				|| sound == SoundType.IRON
				|| sound == SoundType.COPPER
				|| sound == SoundType.CHAIN
				|| sound == SoundType.ANVIL
				|| sound == SoundType.NETHERITE_BLOCK;
	}

	private static boolean isWood(SoundType sound) {
		return sound == SoundType.WOOD
				|| sound == SoundType.BAMBOO
				|| sound == SoundType.BAMBOO_WOOD
				|| sound == SoundType.CHERRY_WOOD
				|| sound == SoundType.NETHER_WOOD
				|| sound == SoundType.LADDER
				|| sound == SoundType.SCAFFOLDING
				|| sound == SoundType.HANGING_SIGN;
	}

	private static boolean isPorousSound(SoundType sound) {
		return sound == SoundType.GRASS
				|| sound == SoundType.WET_GRASS
				|| sound == SoundType.VINE
				|| sound == SoundType.CROP
				|| sound == SoundType.LEAF_LITTER
				|| sound == SoundType.MOSS
				|| sound == SoundType.AZALEA_LEAVES
				|| sound == SoundType.CHERRY_LEAVES
				|| sound == SoundType.COBWEB;
	}

	private static boolean isPorous(AcousticMaterial material) {
		return material == AcousticMaterials.FOLIAGE || material == AcousticMaterials.SOFT;
	}

	private static double collisionFill(VoxelShape shape) {
		if (shape.isEmpty()) {
			return 0.0;
		}
		double volume = 0.0;
		for (AABB box : shape.toAabbs()) {
			volume += Math.max(0.0, box.maxX - box.minX)
					* Math.max(0.0, box.maxY - box.minY)
					* Math.max(0.0, box.maxZ - box.minZ);
		}
		return Math.min(1.0, volume);
	}

	private static void writeString(
			DataOutputStream output,
			String value
	) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		output.writeInt(bytes.length);
		output.write(bytes);
	}

	private record ClassificationEntry(
			String blockId,
			String stateDescriptor,
			String materialId
	) {
	}
}
