package com.tenicana.dronecraft.integrationvalidation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.integration.Aerodynamics4McAtmosphereBridge;
import com.tenicana.dronecraft.mekanism.DroneLoadout;
import com.tenicana.dronecraft.mekanism.DronePowerTier;
import com.tenicana.dronecraft.mekanism.DroneRadioTier;
import com.tenicana.dronecraft.mekanism.MekanismFlightSystem;
import com.tenicana.dronecraft.registry.DroneDataComponents;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Deliberately absent from the production mod source set and jar. */
@Mod(ThirdPartyIntegrationValidationMod.MOD_ID)
public final class ThirdPartyIntegrationValidationMod {
	public static final String MOD_ID = "fpvdrone_integration_validation";
	private static final String PREFIX = "fpvdrone.integrationValidation.";
	private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120L);
	private static final Gson JSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
	private static final String[] CONDITIONAL_RECIPES = {"propeller", "brushless_motor", "esc", "flight_controller",
			"lipo_battery", "carbon_frame", "drone_airframe", "basic_power_module", "advanced_power_module",
			"elite_power_module", "ultimate_power_module", "basic_radio_module", "advanced_radio_module",
			"elite_radio_module", "ultimate_radio_module", "quantum_link_module", "drone_assembly"};
	private static final String[] MEK_INGREDIENTS = {"basic_energy_cube", "elite_energy_cube", "ultimate_energy_cube",
			"energy_tablet", "module_energy_unit", "upgrade_energy", "alloy_infused", "alloy_reinforced", "alloy_atomic",
			"basic_control_circuit", "advanced_control_circuit", "elite_control_circuit", "ultimate_control_circuit",
			"enriched_carbon", "ingot_steel", "teleportation_core", "portable_teleporter", "dust_lithium", "hdpe_sheet",
			"quantum_entangloporter", "pellet_antimatter"};
	private static final String[] REQUIRED_GATES = {"mods_loaded", "mekanism_ingredient_ids_resolved",
			"mekanism_conditional_recipes_loaded", "loaded_recipes_craft_modules", "assembled_loadout_item_persistence",
			"placement_preserves_loadout_tiers", "placed_drone_empty_battery_persistence", "quantum_public_frequency_configured",
			"quantum_real_sided_fe_supply", "quantum_insufficient_supply_fallback", "quantum_source_removed_fallback",
			"a4mc_native_gameplay_sample_available", "a4mc_bridge_matches_native_trust_and_fields"};
	private final UUID runId;
	private final Path reportPath;
	private final Map<String, Gate> gates = new LinkedHashMap<>();
	private final Map<String, Object> observations = new LinkedHashMap<>();
	private final Map<String, CraftingRecipe> recipes = new LinkedHashMap<>();
	private MinecraftServer server;
	private ServerLevel level;
	private long startedNanos;
	private boolean finished;
	private boolean reportOwned;
	private boolean fixturesCreated;
	private int ticks;
	private BlockPos support;
	private BlockPos quantumPosition;
	private PublicMekanismFixture quantumFixture;
	private String quantumSetupError;
	private boolean quantumChecksFinished;
	private ItemStack assembled = ItemStack.EMPTY;
	private DroneEntity placed;
	private FakePlayer fixturePlayer;
	private boolean fixturePlayerAdded;
	private boolean placementChecked;

	public ThirdPartyIntegrationValidationMod() {
		if (!Boolean.getBoolean(PREFIX + "enabled")) {
			runId = null;
			reportPath = null;
			return;
		}
		if (!Boolean.getBoolean(PREFIX + "isolated")) {
			throw new IllegalArgumentException("Integration validation requires explicit isolated=true");
		}
		String id = System.getProperty(PREFIX + "runId", "");
		runId = UUID.fromString(id);
		if (!runId.toString().equalsIgnoreCase(id)) throw new IllegalArgumentException("runId must be a full UUID");
		reportPath = Path.of(System.getProperty(PREFIX + "report", ""));
		if (!reportPath.isAbsolute()) throw new IllegalArgumentException("Validation report must use an absolute path");
		for (String gate : REQUIRED_GATES) gates.put(gate, new Gate("PENDING", "Not yet observed"));
		NeoForge.EVENT_BUS.addListener(this::started);
		NeoForge.EVENT_BUS.addListener(this::tick);
	}

	private void started(ServerStartedEvent event) {
		server = event.getServer();
		level = server.overworld();
		startedNanos = System.nanoTime();
		try {
			Files.createDirectories(reportPath.getParent());
			Files.writeString(reportPath, JSON.toJson(report(false)), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			reportOwned = true;
			check(server.isDedicatedServer(), "Validator only runs in an isolated dedicated server");
			check(server.getPlayerList().getPlayerCount() == 0, "Refusing a server with connected real players");
			check("1.21.1".equals(SharedConstants.getCurrentVersion().getName()), "This validator targets Minecraft 1.21.1 only");
			runCheck("mods_loaded", () -> {
				Map<String, String> versions = new LinkedHashMap<>();
				for (String mod : List.of("fpvdrone", "mekanism", "aerodynamics4mc")) {
					versions.put(mod, ModList.get().getModContainerById(mod)
							.map(container -> container.getModInfo().getVersion().toString()).orElse("missing"));
					check(ModList.get().isLoaded(mod), "Required actual mod is missing: " + mod);
				}
				observations.put("loadedModVersions", versions);
			});
		} catch (Exception | LinkageError error) {
			observations.put("startupFailure", describe(error));
			unavailablePending("Startup validation failed: " + describe(error));
			finish();
		}
	}

	private void tick(ServerTickEvent.Post event) {
		if (server == null || event.getServer() != server || finished) return;
		if (System.nanoTime() - startedNanos >= TIMEOUT_NANOS) {
			unavailablePending("120-second deadline: native observations did not satisfy the required gate");
			finish();
			return;
		}
		if (server.getPlayerList().getPlayerCount() != 0) {
			unavailablePending("Real player connected during disposable validation");
			finish();
			return;
		}
		ticks++;
		if (!fixturesCreated) {
			fixturesCreated = true;
			try { createFixtures(); }
			catch (Exception | LinkageError error) {
				observations.put("fixtureFailure", describe(error));
				unavailablePending("Fixture creation failed: " + describe(error));
				finish();
				return;
			}
		}
		// Loading a FULL chunk does not immediately promote its native entity sections to ticking.
		// Run placement only after the forced ticket has completed that promotion.
		if (!placementChecked && ticks >= 5) {
			boolean entityTicking = level.isPositionEntityTicking(support);
			observations.put("fixtureChunk", Map.of("entityTicking", entityTicking, "validationTick", ticks));
			if (entityTicking) {
				placementChecked = true;
				try { addNativePlayerAnchor(); }
				catch (Exception | LinkageError error) { observations.put("nativePlayerAnchorFailure", describe(error)); }
				validatePlacement();
			}
		}
		if (!quantumChecksFinished && placementChecked) pollQuantum();
		if (ticks % 5 == 0 && pending("a4mc_bridge_matches_native_trust_and_fields")) sampleA4mc();
		if (gates.values().stream().noneMatch(gate -> "PENDING".equals(gate.status()))) finish();
	}

	private void createFixtures() {
		support = new BlockPos(68, 80, 68);
		quantumPosition = support.offset(3, 1, 3);
		level.getChunkAt(support);
		level.setChunkForced(support.getX() >> 4, support.getZ() >> 4, true);
		for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
			level.setBlockAndUpdate(support.offset(x, 0, z), Blocks.STONE.defaultBlockState());
			for (int y = 1; y <= 4; y++) level.setBlockAndUpdate(support.offset(x, y, z), Blocks.AIR.defaultBlockState());
		}
		Block quantum = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("mekanism", "quantum_entangloporter"));
		check(quantum != Blocks.AIR, "Actual quantum_entangloporter block is absent");
		check(level.setBlockAndUpdate(quantumPosition, quantum.defaultBlockState()), "Native quantum block placement failed");
		try { quantumFixture = new PublicMekanismFixture(level, quantumPosition, runId); }
		catch (Exception | LinkageError error) { quantumSetupError = describe(error); }
		fixturePlayer = FakePlayerFactory.get(level, new GameProfile(runId, "FPVIntegration"));
		GameType.SURVIVAL.updatePlayerAbilities(fixturePlayer.getAbilities());
		fixturePlayer.setPos(support.getX() + 2.25, support.getY() + 1.0, support.getZ() + 0.5);
		validateRecipes();
		validateAssembly();
	}

	private void addNativePlayerAnchor() {
		// A4MC 0.2.1 builds meteorological coverage only for levels containing native players.
		// The NeoForge test actor activates that real runtime path; no wind values/provider are injected.
		level.addNewPlayer(fixturePlayer);
		fixturePlayerAdded = true;
		boolean nativeLevelPlayer = level.players().contains(fixturePlayer);
		observations.put("a4mcPlayerAnchor", Map.of("type", "NeoForge FakePlayer in native ServerLevel",
				"uuid", fixturePlayer.getUUID().toString(), "nativeLevelPlayer", nativeLevelPlayer,
				"connectedPlayers", server.getPlayerList().getPlayerCount(),
				"provider", "Unmodified actual A4MC server runtime"));
		check(nativeLevelPlayer, "Native level did not register the disposable player anchor");
	}

	private void validateRecipes() {
		runCheck("mekanism_ingredient_ids_resolved", () -> {
			List<String> resolved = new ArrayList<>();
			for (String name : MEK_INGREDIENTS) {
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath("mekanism", name);
				check(BuiltInRegistries.ITEM.containsKey(id), "Missing real Mekanism ingredient " + id);
				resolved.add(id.toString());
			}
			observations.put("mekanismIngredientIds", resolved);
		});
		runCheck("mekanism_conditional_recipes_loaded", () -> {
			for (String name : CONDITIONAL_RECIPES) {
				var holder = server.getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("fpvdrone", name));
				check(holder.isPresent(), "Real datapack did not resolve conditional recipe fpvdrone:" + name);
				check(holder.get().value() instanceof CraftingRecipe, "Recipe is not a native crafting recipe: " + name);
				recipes.put(name, (CraftingRecipe) holder.get().value());
			}
			observations.put("conditionalRecipeIds", new ArrayList<>(recipes.keySet()));
		});
		runCheck("loaded_recipes_craft_modules", () -> {
			check(recipes.size() == CONDITIONAL_RECIPES.length, "Conditional recipe inventory is incomplete");
			Map<String, Object> results = new LinkedHashMap<>();
			for (var entry : recipes.entrySet()) {
				if ("drone_assembly".equals(entry.getKey())) continue;
				CraftingRecipe recipe = entry.getValue();
				List<ItemStack> inputs = new ArrayList<>();
				for (Ingredient ingredient : recipe.getIngredients()) {
					ItemStack[] choices = ingredient.getItems();
					inputs.add(choices.length == 0 ? ItemStack.EMPTY : choices[0].copyWithCount(1));
				}
				int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : inputs.size();
				int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : 1;
				CraftingInput input = CraftingInput.of(width, height, inputs);
				check(recipe.matches(input, level), "Loaded recipe rejects its resolved ingredients: " + entry.getKey());
				ItemStack output = recipe.assemble(input, level.registryAccess());
				String id = BuiltInRegistries.ITEM.getKey(output.getItem()).toString();
				check(!output.isEmpty() && id.equals("fpvdrone:" + entry.getKey()), "Wrong native recipe output: " + entry.getKey());
				results.put(entry.getKey(), Map.of("item", id, "count", output.getCount()));
			}
			check(results.size() == 16, "Did not craft all sixteen loaded component recipes");
			observations.put("craftedComponents", results);
		});
	}

	private void validateAssembly() {
		DroneLoadout expected = new DroneLoadout(DronePowerTier.ADVANCED, DroneRadioTier.ELITE, true);
		runCheck("assembled_loadout_item_persistence", () -> {
			CraftingRecipe recipe = recipes.get("drone_assembly");
			check(recipe != null, "Actual loaded assembly recipe is missing");
			CraftingInput input = CraftingInput.of(2, 2, List.of(new ItemStack(DroneItems.droneAirframe()),
					new ItemStack(DroneItems.powerModule(expected.powerTier())),
					new ItemStack(DroneItems.radioModule(expected.radioTier())), new ItemStack(DroneItems.quantumLinkModule())));
			check(recipe.matches(input, level), "Loaded assembly recipe did not match real module items");
			ItemStack result = recipe.assemble(input, level.registryAccess());
			check(result.is(DroneItems.drone()) && result.getCount() == 1, "Assembly did not return exactly one placement item");
			check(expected.equals(result.get(DroneDataComponents.DRONE_LOADOUT.get())), "Assembly lost selected tiers");
			var ops = RegistryOps.create(NbtOps.INSTANCE, level.registryAccess());
			var encoded = ItemStack.CODEC.encodeStart(ops, result).getOrThrow();
			assembled = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
			check(expected.equals(assembled.get(DroneDataComponents.DRONE_LOADOUT.get())), "Native item NBT codec lost loadout");
			observations.put("assembledLoadout", Map.of("power", "advanced", "radio", "elite", "quantum", true));
		});
	}

	private void validatePlacement() {
		DroneLoadout expected = new DroneLoadout(DronePowerTier.ADVANCED, DroneRadioTier.ELITE, true);
		runCheck("placement_preserves_loadout_tiers", () -> {
			check(!assembled.isEmpty(), "No persisted assembled item available for placement");
			FakePlayer player = fixturePlayer;
			AABB fixtureBounds = new AABB(support).inflate(2.0);
			var priorIds = level.getEntitiesOfClass(DroneEntity.class, fixtureBounds).stream()
					.map(Entity::getUUID).collect(java.util.stream.Collectors.toSet());
			player.setItemInHand(InteractionHand.MAIN_HAND, assembled.copy());
			UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
					new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false));
			check(DroneItems.drone().useOn(context).consumesAction(), "Production placement failed in the native server level");
			check(player.getMainHandItem().isEmpty(), "Survival placement did not consume the assembled item");
			var allDrones = level.getEntitiesOfClass(DroneEntity.class, fixtureBounds);
			var drones = allDrones.stream().filter(drone -> !priorIds.contains(drone.getUUID())).toList();
			observations.put("placementQuery", Map.of("nativeDroneCount", allDrones.size(),
					"priorNativeDroneCount", priorIds.size(), "newNativeDroneCount", drones.size(),
					"priorNativeDroneUuids", priorIds.stream().map(UUID::toString).sorted().toList(),
					"entityTicking", level.isPositionEntityTicking(support), "validationTick", ticks));
			check(drones.size() == 1, "Placement query returned " + drones.size() + " new native drones; expected exactly one");
			placed = drones.getFirst();
			CompoundTag saved = placed.saveWithoutId(new CompoundTag());
			check(placed.hasMekanismLoadout() && !placed.hasOwner() && !placed.isArmed(), "Placed loadout was not configured/disarmed/unbound");
			check(saved.getBoolean("mek_configured") && "advanced".equals(saved.getString("mek_power_tier"))
					&& "elite".equals(saved.getString("mek_radio_tier")) && saved.getBoolean("mek_quantum_link"), "Placed entity lost tiers");
			check(saved.getLong("mek_energy_fe") == expected.powerTier().capacityFe(), "Placement initialized incorrect energy capacity");
			check(BlockPos.of(saved.getLong("mek_quantum_source")).equals(quantumPosition), "Placement did not capture the real nearby quantum block");
			observations.put("placedDrone", Map.of("uuid", placed.getUUID().toString(), "energyFe", saved.getLong("mek_energy_fe"),
					"quantumSource", quantumPosition.toShortString(), "radioRangeMeters", placed.getMekanismMaxRangeMeters()));
		});
		runCheck("placed_drone_empty_battery_persistence", () -> {
			check(placed != null, "No actual placed entity available for persistence");
			CompoundTag saved = placed.saveWithoutId(new CompoundTag());
			saved.putLong("mek_energy_fe", 0L);
			Path directory = server.getServerDirectory().toAbsolutePath().normalize()
					.resolve("fpvdrone-integration-validation").resolve(runId.toString());
			Files.createDirectories(directory);
			Path file = directory.resolve("empty-drone.nbt");
			check(!Files.exists(file), "Run UUID reused an existing NBT artifact");
			NbtIo.writeCompressed(saved, file);
			CompoundTag disk = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			DroneEntity restored = new DroneEntity(DroneEntityTypes.drone(), level);
			restored.load(disk);
			restored.tick();
			CompoundTag again = restored.saveWithoutId(new CompoundTag());
			check(again.getBoolean("mek_configured") && again.getLong("mek_energy_fe") == 0L
					&& restored.getMekanismChargeFraction() == 0.0f, "Disk NBT reload or native entity tick refilled the empty battery");
			check("advanced".equals(again.getString("mek_power_tier")) && "elite".equals(again.getString("mek_radio_tier")), "Reload lost tiers");
			check(again.getLong("mek_quantum_source") == saved.getLong("mek_quantum_source"), "Reload rebound the stored source");
			observations.put("emptyBatteryNbt", Map.of("path", file.toString(), "energyFeAfterReloadAndTick", 0L,
					"verification", "disk compressed NBT -> Entity.load -> Entity.tick -> Entity.saveWithoutId"));
		});
	}

	private void pollQuantum() {
		if (quantumFixture == null) {
			gates.put("quantum_public_frequency_configured", new Gate("UNAVAILABLE", quantumSetupError));
			unavailableQuantum("Real public frequency fixture could not be configured: " + quantumSetupError);
			return;
		}
		try {
			if (!quantumFixture.frequencyReady()) {
				observations.put("quantumFrequencyLastState", "Native tile.getFreq()/hasFrequency has not become ready");
				return;
			}
			gates.put("quantum_public_frequency_configured", new Gate("PASS", "Observed native frequency and energy container"));
			MekanismFlightSystem system = new MekanismFlightSystem();
			Vec3 position = Vec3.atCenterOf(support.above());
			system.configure(new DroneLoadout(DronePowerTier.ADVANCED, DroneRadioTier.ELITE, true), level, position);
			check(quantumPosition.equals(system.quantumSource()), "Production source search did not select the real fixture block");
			DroneInput armed = new DroneInput(0.0, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE);
			runCheck("quantum_real_sided_fe_supply", () -> {
				quantumFixture.fill();
				var capability = PublicMekanismFixture.extractingCapability(level, quantumPosition, 200);
				long nativeBefore = quantumFixture.energy();
				int feBefore = capability.getEnergyStored();
				long batteryBefore = system.storedEnergyFe();
				var status = system.update(level, position, armed);
				long nativeAfter = quantumFixture.energy();
				int feAfter = capability.getEnergyStored();
				check(status.quantumActive() && status.hasPower() && system.storedEnergyFe() == batteryBefore,
						"Real quantum supply did not replace this tick's battery demand");
				check(nativeAfter < nativeBefore && feAfter < feBefore, "Native frequency and FE capability did not actually lose energy");
				observations.put("quantumSupply", Map.of("nativeEnergyBefore", nativeBefore, "nativeEnergyAfter", nativeAfter,
						"feBefore", feBefore, "feAfter", feAfter, "batteryFeBefore", batteryBefore, "batteryFeAfter", system.storedEnergyFe()));
			});
			runCheck("quantum_insufficient_supply_fallback", () -> {
				quantumFixture.setEnergy(1L);
				long nativeBefore = quantumFixture.energy();
				int available = PublicMekanismFixture.availableFe(level, quantumPosition, 200);
				check(nativeBefore > 0L && available < 200, "Fixture does not have positive but insufficient real native energy");
				long batteryBefore = system.storedEnergyFe();
				var status = system.update(level, position, armed);
				check(!status.quantumActive() && status.hasPower() && system.storedEnergyFe() == batteryBefore - 200L,
						"Insufficient actual frequency did not fall back to the onboard battery");
				check(quantumFixture.energy() == nativeBefore, "Production probe consumed a source that cannot satisfy demand");
				observations.put("insufficientSupply", Map.of("nativeEnergy", quantumFixture.energy(), "simulatedAvailableFe", available,
						"batteryFeBefore", batteryBefore, "batteryFeAfter", system.storedEnergyFe()));
			});
			runCheck("quantum_source_removed_fallback", () -> {
				check(level.removeBlock(quantumPosition, false), "Native source removal failed");
				long batteryBefore = system.storedEnergyFe();
				var status = system.update(level, position, armed);
				check(level.getBlockEntity(quantumPosition) == null && !status.quantumActive()
						&& system.storedEnergyFe() == batteryBefore - 200L && quantumPosition.equals(system.quantumSource()),
						"Removed actual source did not fall back while retaining its captured position");
				observations.put("removedSource", Map.of("batteryFeBefore", batteryBefore,
						"batteryFeAfter", system.storedEnergyFe(), "capturedSourceRetained", true));
			});
			quantumChecksFinished = true;
		} catch (Exception | LinkageError error) {
			if (pending("quantum_public_frequency_configured")) {
				gates.put("quantum_public_frequency_configured", new Gate("UNAVAILABLE", describe(error)));
			} else if (pending("quantum_real_sided_fe_supply")) {
				gates.put("quantum_real_sided_fe_supply", new Gate("FAIL", describe(error)));
			}
			unavailableQuantum("Actual native frequency/capability setup failed: " + describe(error));
		}
	}

	private void sampleA4mc() {
		try {
			observations.put("a4mcRuntime", NativeA4mcObservation.runtimeReport());
			Vec3 position = Vec3.atCenterOf(support.above(3));
			NativeA4mcObservation before = NativeA4mcObservation.sample(level, position);
			var bridge = Aerodynamics4McAtmosphereBridge.sampleGameplay(level, position.x, position.y, position.z);
			NativeA4mcObservation after = NativeA4mcObservation.sample(level, position);
			observations.put("a4mcNativeBefore", before.report());
			observations.put("a4mcNativeAfter", after.report());
			observations.put("a4mcBridge", bridge);
			if (!before.flow() || !before.trusted() || !before.finite()) return;
			gates.put("a4mc_native_gameplay_sample_available", new Gate("PASS", "Actual server-only API returned trusted finite flow"));
			// The production bridge makes its own public sample call; accept either observed native snapshot
			// when asynchronous publication changes a field between the adjacent calls.
			if (bridge.trustedForGameplay()
					&& (before.matches(bridge) || after.matches(bridge))) {
				gates.put("a4mc_bridge_matches_native_trust_and_fields", new Gate("PASS", "Production bridge matches actual native fields/trust/freshness"));
			} else {
				observations.put("a4mcLastMismatch", "Bridge did not match either adjacent actual public API observation; awaiting a stable native sample");
			}
		} catch (Exception | LinkageError error) {
			if (error instanceof java.lang.reflect.InvocationTargetException) {
				observations.put("a4mcLastPublicSampleFailure", describe(error));
				return;
			}
			gates.put("a4mc_native_gameplay_sample_available", new Gate("UNAVAILABLE", describe(error)));
			gates.put("a4mc_bridge_matches_native_trust_and_fields", new Gate("UNAVAILABLE", "Native API observation failed: " + describe(error)));
		}
	}

	private void unavailableQuantum(String reason) {
		for (String gate : List.of("quantum_real_sided_fe_supply", "quantum_insufficient_supply_fallback", "quantum_source_removed_fallback")) {
			if (pending(gate)) gates.put(gate, new Gate("UNAVAILABLE", reason));
		}
		quantumChecksFinished = true;
	}
	private boolean pending(String gate) { return "PENDING".equals(gates.get(gate).status()); }
	private void unavailablePending(String reason) {
		for (String gate : REQUIRED_GATES) if (pending(gate)) gates.put(gate, new Gate("UNAVAILABLE", reason));
	}
	private void runCheck(String gate, CheckedAction action) {
		try {
			action.run();
			gates.put(gate, new Gate("PASS", "Observed all required assertions"));
		} catch (Exception | LinkageError error) {
			gates.put(gate, new Gate("FAIL", describe(error)));
		}
	}
	private void finish() {
		if (finished) return;
		finished = true;
		try {
			if (placed != null) placed.discard();
			if (fixturePlayerAdded) level.removePlayerImmediately(fixturePlayer, Entity.RemovalReason.DISCARDED);
			if (support != null) level.setChunkForced(support.getX() >> 4, support.getZ() >> 4, false);
			if (reportOwned) {
				Files.writeString(reportPath, JSON.toJson(report(true)), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
			}
		} catch (Exception error) {
			System.err.println("FPV integration validation report write failed: " + describe(error));
		} finally {
			if (server != null && server.isDedicatedServer()) server.halt(false);
		}
	}
	private Map<String, Object> report(boolean complete) {
		Map<String, Object> report = new LinkedHashMap<>();
		long elapsedNanos = startedNanos == 0L ? 0L : System.nanoTime() - startedNanos;
		report.put("runId", runId.toString());
		report.put("minecraft", SharedConstants.getCurrentVersion().getName());
		report.put("generatedAt", Instant.now().toString());
		report.put("complete", complete);
		report.put("passed", complete && elapsedNanos < TIMEOUT_NANOS
				&& gates.values().stream().allMatch(gate -> "PASS".equals(gate.status())));
		report.put("elapsedSeconds", elapsedNanos / 1.0e9);
		report.put("deadlineExceeded", elapsedNanos >= TIMEOUT_NANOS);
		report.put("timeoutSeconds", 120);
		report.put("requiredGates", List.of(REQUIRED_GATES));
		report.put("gates", gates);
		report.put("observations", observations);
		return report;
	}
	private static void check(boolean condition, String reason) {
		if (!condition) throw new IllegalStateException(reason);
	}
	private static String describe(Throwable error) {
		Throwable cause = error;
		while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
		return cause.getClass().getName() + ": " + cause.getMessage();
	}
	private record Gate(String status, String reason) { }
	@FunctionalInterface private interface CheckedAction { void run() throws Exception; }
}
