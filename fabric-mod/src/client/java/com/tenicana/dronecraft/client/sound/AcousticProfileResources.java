package com.tenicana.dronecraft.client.sound;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.acoustics.AcousticProfileCatalog;
import com.tenicana.dronecraft.acoustics.AcousticProfileKey;
import com.tenicana.dronecraft.acoustics.AcousticSourceProfile;
import com.tenicana.dronecraft.entity.DroneEntity;

/**
 * Client resource-pack loader for namespaced
 * {@code assets/<namespace>/acoustic_profiles/<profile>.json} resources.
 */
public final class AcousticProfileResources
		implements SimpleSynchronousResourceReloadListener {
	private static final String DIRECTORY = "acoustic_profiles";
	private static final AcousticProfileResources INSTANCE =
			new AcousticProfileResources();
	private static volatile AcousticProfileCatalog catalog =
			AcousticProfileCatalog.researchFallbackOnly();

	private AcousticProfileResources() {
	}

	public static void initialize() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES)
				.registerReloadListener(INSTANCE);
	}

	static AcousticSourceProfile select(DroneEntity drone) {
		AcousticProfileKey key = AcousticProfileKey.fromMetres(
				drone.getAirframePreset(),
				drone.getRotorCount(),
				drone.getRotorBladeCount(),
				drone.getRotorRadiusMeters(),
				drone.getMotorPolePairs()
		);
		return catalog.select(key);
	}

	static AcousticProfileCatalog snapshot() {
		return catalog;
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath(
				FpvDronecraftMod.MOD_ID,
				"acoustic_profiles"
		);
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, Resource> resources = manager.listResources(
				DIRECTORY,
				id -> id.getPath().endsWith(".json")
		);
		List<Map.Entry<Identifier, Resource>> ordered =
				new ArrayList<>(resources.entrySet());
		ordered.sort(Map.Entry.comparingByKey(Comparator.naturalOrder()));
		List<AcousticSourceProfile> profiles =
				new ArrayList<>(ordered.size());
		try {
			for (Map.Entry<Identifier, Resource> entry : ordered) {
				try (Reader reader = entry.getValue().openAsReader()) {
					AcousticSourceProfile profile =
							AcousticProfileJsonDecoder.decode(reader);
					String expectedId = profileId(entry.getKey());
					if (!profile.id().equals(expectedId)) {
						throw new IllegalArgumentException(
								"$.id: expected " + expectedId
										+ " for resource " + entry.getKey()
						);
					}
					profiles.add(profile);
				}
			}
			AcousticProfileCatalog next = new AcousticProfileCatalog(
					profiles,
					AcousticSourceProfile.researchFallback()
			);
			catalog = next;
			FpvDronecraftMod.LOGGER.info(
					"Loaded {} measured acoustic source profiles",
					next.profiles().size()
			);
		} catch (IOException | RuntimeException error) {
			FpvDronecraftMod.LOGGER.error(
					"Rejected acoustic profile reload; retaining previous immutable catalog",
					error
			);
		}
	}

	private static String profileId(Identifier resourceId) {
		String path = resourceId.getPath();
		String prefix = DIRECTORY + "/";
		String suffix = ".json";
		if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
			throw new IllegalArgumentException(
					"invalid acoustic profile resource path " + resourceId
			);
		}
		return resourceId.getNamespace() + ":"
				+ path.substring(prefix.length(), path.length() - suffix.length());
	}
}
