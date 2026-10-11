package com.tenicana.dronecraft.gametest;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.tenicana.dronecraft.assembly.DroneAssemblyInventory;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.debug.DroneDebugSettings;
import com.tenicana.dronecraft.debug.DroneDebugSettings.FlightModelMode;
import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.menu.DroneAssemblyMenu;
import com.tenicana.dronecraft.mekanism.DroneLoadout;
import com.tenicana.dronecraft.mekanism.DronePowerTier;
import com.tenicana.dronecraft.mekanism.DroneRadioTier;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneItems;
import com.tenicana.dronecraft.sim.DroneInput;
import com.tenicana.dronecraft.sim.FlightMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("fpvdrone")
@PrefixGameTestTemplate(false)
public final class DroneAssemblyGameTest {
	private static final String ENERGY_KEY = "fpvdrone_energy_fe";
	private static final DroneInput DISARMED = new DroneInput(0.0, 0.0, 0.0, 0.0, false, true, FlightMode.ANGLE);

	private DroneAssemblyGameTest() { }

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void native_menu_transfers_thirteen_components_without_loss(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		DroneEntity drone = fixture.drone();
		Player player = fixture.player();
		drone.prepareAssemblyTransfer();
		List<ItemStack> expected = drone.assemblyInventory().contentsSnapshot();
		drone.resetAssemblyToBareFrame();
		DroneAssemblyMenu menu = new DroneAssemblyMenu(1, player.getInventory(), drone);
		DroneAssemblyMenu concurrentMenu = new DroneAssemblyMenu(2, player.getInventory(), drone);
		assertTrue(menu.slots.size() == 49, "native menu did not expose 13 components and 36 backpack slots");
		for (int component = 0; component < 13; component++) {
			player.getInventory().setItem(9 + component, expected.get(component).copy());
			assertTrue(!menu.quickMoveStack(player, 13 + component).isEmpty(), "shift insertion failed for component " + component);
			assertTrue(player.getInventory().getItem(9 + component).isEmpty(), "insertion left a duplicated backpack component " + component);
			assertStack(expected.get(component), drone.assemblyInventory().getItem(component), "installed component " + component);
		}
		assertTrue(drone.isAssemblyComplete() && drone.getAssemblyMask() == DroneAssemblyInventory.FULL_MASK, "13 installed pieces did not complete the synchronized assembly");
		assertTrue(backpackCount(player) == 0, "assembly consumed the wrong backpack count");
		for (int component = 0; component < 13; component++) {
			assertTrue(!menu.quickMoveStack(player, component).isEmpty(), "shift removal failed for component " + component);
			assertTrue(concurrentMenu.quickMoveStack(player, component).isEmpty(), "second open menu duplicated component " + component);
		}
		assertTrue(drone.assemblyInventory().isEmpty() && drone.getAssemblyMask() == 0, "removal did not leave the bare frame");
		assertTrue(backpackCount(player) == 13, "round trip lost or duplicated components");
		assertMultiset(expected, backpackStacks(player), "component backpack round trip");
		drone.discard();
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void native_menu_rejects_wrong_items_and_preserves_full_backpack(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		DroneEntity drone = fixture.drone();
		Player player = fixture.player();
		drone.resetAssemblyToBareFrame();
		DroneAssemblyMenu menu = new DroneAssemblyMenu(1, player.getInventory(), drone);
		player.getInventory().setItem(9, new ItemStack(DroneItems.brushlessMotor(), 64));
		menu.clicked(13, 0, ClickType.PICKUP, player);
		menu.clicked(0, 0, ClickType.PICKUP, player);
		assertTrue(drone.assemblyInventory().getItem(0).getCount() == 1 && menu.getCarried().getCount() == 63, "native click bypassed the one-component slot limit");
		menu.clicked(14, 0, ClickType.PICKUP, player);
		player.getInventory().setItem(11, new ItemStack(Items.DIRT, 12));
		menu.clicked(15, 0, ClickType.PICKUP, player);
		menu.clicked(4, 0, ClickType.PICKUP, player);
		assertTrue(drone.assemblyInventory().getItem(4).isEmpty() && menu.getCarried().getCount() == 12, "unrelated item entered a propeller slot");
		assertTrue(!menu.getSlot(4).mayPlace(new ItemStack(DroneItems.brushlessMotor())), "motor was accepted in a propeller slot");
		menu.clicked(15, 0, ClickType.PICKUP, player);
		for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
		ItemStack retained = drone.assemblyInventory().getItem(0).copy();
		assertTrue(menu.quickMoveStack(player, 0).isEmpty(), "full backpack unexpectedly accepted a component");
		assertStack(retained, drone.assemblyInventory().getItem(0), "full backpack retained component");
		assertTrue(backpackCount(player) == 36 * 64 && drone.getAssemblyMask() == 1, "failed transfer deleted or duplicated inventory contents");
		drone.discard();
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void grounded_frame_native_menu_enforces_owner_and_flight_locks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
		BlockPos support = helper.absolutePos(new BlockPos(1, 2, 1));
		level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
		placePlayer(owner, new Vec3(support.getX() + 0.5, support.getY() + 1.04, support.getZ() + 0.5));
		owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.carbonFrame()));
		UseOnContext placement = new UseOnContext(owner, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false));
		assertTrue(owner.getMainHandItem().useOn(placement).consumesAction(), "native placement failed");
		List<DroneEntity> placed = level.getEntitiesOfClass(DroneEntity.class, new AABB(support).inflate(2.0));
		assertTrue(placed.size() == 1, "native placement did not create exactly one frame");
		DroneEntity drone = placed.getFirst();
		drone.tick();
		try {
			placePlayer(owner, drone.position());
			assertTrue(owner.getMainHandItem().isEmpty() && drone.getAssemblyMask() == 0,
					"native survival frame placement did not consume one item and leave a bare aircraft");
			assertTrue(!drone.onGround(), "ground-lock fixture unexpectedly relied on native onGround");
			assertTrue(drone.interact(owner, InteractionHand.MAIN_HAND).consumesAction(), "empty-hand native interaction failed on the real support surface");
			assertTrue(drone.isOwnedBy(owner.getUUID()), "native frame placement did not assign its owner");
			// The ordinary mock has no mod-negotiated client; real openMenu packets are covered by the client-world test.
			DroneAssemblyMenu menu = new DroneAssemblyMenu(1, owner.getInventory(), drone);
			assertTrue(menu.stillValid(owner), "the real grounded frame did not permit native menu access");
			drone.assemblyInventory().fillPrototype();
			Player other = helper.makeMockPlayer(GameType.SURVIVAL);
			other.setUUID(UUID.randomUUID());
			placePlayer(other, drone.position());
			assertMenuLocked(menu, other, "other owner");
			Vec3 ownerPosition = owner.position();
			owner.setPos(owner.getX() + 12.0, owner.getY(), owner.getZ());
			assertMenuLocked(menu, owner, "out of reach");
			owner.setPos(ownerPosition.x(), ownerPosition.y(), ownerPosition.z());
			Vec3 groundPosition = drone.position();
			drone.setPos(drone.getX(), drone.getY() + 3.0, drone.getZ());
			assertMenuLocked(menu, owner, "airborne frame");
			drone.setPos(groundPosition.x(), groundPosition.y(), groundPosition.z());
			armDiagnostic(drone, owner.getUUID());
			assertTrue(drone.isArmed(), "flight lock fixture did not arm through the server input route");
			assertMenuLocked(menu, owner, "armed frame");
			disarm(drone, owner.getUUID());
			drone.setPos(groundPosition.x(), groundPosition.y(), groundPosition.z());
			assertTrue(menu.stillValid(owner), "grounded disarmed owner did not regain assembly access");
		} finally {
			DroneControlManager.stopDiagnostic(owner.getUUID());
			DroneControlManager.clearInput(owner.getUUID());
			drone.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void every_missing_required_component_blocks_both_native_flight_routes(GameTestHelper helper) {
		for (FlightModelMode mode : List.of(FlightModelMode.PLAYABLE, FlightModelMode.SIMULATION)) {
			UUID owner = UUID.randomUUID();
			DroneEntity drone = spawnDrone(helper, owner, mode);
			try {
				armDiagnostic(drone, owner);
				assertTrue(drone.isArmed(), "complete assembly did not arm in " + mode);
				disarm(drone, owner);
				for (int missing = 0; missing < DroneAssemblyInventory.CAMERA; missing++) {
					ItemStack removed = drone.assemblyInventory().removeItem(missing, 1);
					armDiagnostic(drone, owner);
					assertTrue(!drone.isArmed(), "server armed with missing component " + missing + " in " + mode);
					assertTrue(!drone.isFlightReady() && !drone.isAssemblyComplete() && (drone.getAssemblyMask() & (1 << missing)) == 0,
							"missing required component did not update flight readiness");
					assertTrue(drone.getControlThrottle() == 0.0f, "incomplete aircraft retained a powered throttle command in " + mode);
					DroneControlManager.stopDiagnostic(owner);
					DroneControlManager.clearInput(owner);
					drone.assemblyInventory().setItem(missing, removed);
				}
			} finally {
				DroneControlManager.stopDiagnostic(owner);
				DroneControlManager.clearInput(owner);
				drone.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void camera_optional_native_flight_remains_ready_in_both_routes(GameTestHelper helper) {
		assertTrue(DroneAssemblyInventory.FULL_MASK == 8191 && DroneAssemblyInventory.REQUIRED_FLIGHT_MASK == 4095,
				"camera optionality changed the thirteen-slot visual mask");
		for (FlightModelMode mode : List.of(FlightModelMode.PLAYABLE, FlightModelMode.SIMULATION)) {
			UUID owner = UUID.randomUUID();
			DroneEntity drone = spawnDrone(helper, owner, mode);
			try {
				armDiagnostic(drone, owner);
				assertTrue(drone.isArmed() && drone.hasCamera(), "complete camera-removal fixture did not arm in " + mode);
				ItemStack camera = drone.assemblyInventory().removeItem(DroneAssemblyInventory.CAMERA, 1);
				assertTrue(drone.isFlightReady() && drone.assemblyInventory().isFlightReady()
						&& !drone.hasCamera() && !drone.isAssemblyComplete()
						&& drone.getAssemblyMask() == DroneAssemblyInventory.REQUIRED_FLIGHT_MASK,
						"camera removal changed required hardware readiness in " + mode);
				assertTrue(drone.isArmed(), "optional camera removal disarmed line-of-sight flight in " + mode);
				DroneControlManager.stopDiagnostic(owner);
				DroneControlManager.update(owner, drone.getUUID(), new DroneInput(0.02, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE), drone.tickCount);
				drone.tick();
				double initialY = drone.getY();
				for (int tick = 0; tick < 20; tick++) {
					DroneControlManager.update(owner, drone.getUUID(), new DroneInput(1.0, 0.0, 0.0, 0.0, true, true, FlightMode.ANGLE), drone.tickCount);
					drone.tick();
					assertTrue(drone.isArmed() && drone.isFlightReady(), "camera-free native flight rejected live control in " + mode);
				}
				assertTrue(drone.getY() > initialY + 0.02, "camera-free aircraft did not lift off through native " + mode + " physics");
				disarm(drone, owner);
				DroneEntity reopened = restoredDrone(helper.getLevel(), save(drone));
				assertTrue(reopened.isFlightReady() && !reopened.hasCamera() && !reopened.isAssemblyComplete()
						&& reopened.getAssemblyMask() == DroneAssemblyInventory.REQUIRED_FLIGHT_MASK,
						"camera-free save was refilled or lost flight readiness in " + mode);
				drone.assemblyInventory().setItem(DroneAssemblyInventory.CAMERA, camera);
				assertTrue(drone.hasCamera() && drone.isAssemblyComplete() && drone.isFlightReady()
						&& drone.getAssemblyMask() == DroneAssemblyInventory.FULL_MASK,
						"reinstalling the camera did not restore the complete visual assembly in " + mode);
			} finally {
				DroneControlManager.stopDiagnostic(owner);
				DroneControlManager.clearInput(owner);
				drone.discard();
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void native_entity_save_preserves_partial_components_and_migrates_legacy(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		DroneEntity drone = fixture.drone();
		drone.resetAssemblyToBareFrame();
		ItemStack markedMotor = new ItemStack(DroneItems.brushlessMotor());
		markedMotor.set(DataComponents.CUSTOM_NAME, Component.literal("Saved motor"));
		CustomData.update(DataComponents.CUSTOM_DATA, markedMotor, tag -> {
			tag.putString("external_marker", "keep-me");
			tag.putInt("external_serial", 1742);
			tag.putByte("external_byte", (byte) 12);
			tag.putShort("external_short", (short) 12);
			tag.putLong("external_long", 9_007_199_254_740_993L);
			tag.putFloat("external_float", 0.25f);
			tag.putDouble("external_double", 0.25);
			tag.putByteArray("external_bytes", new byte[] { 1, -2, 3 });
			tag.putIntArray("external_ints", new int[] { 1742, Integer.MAX_VALUE });
			tag.putLongArray("external_longs", new long[] { 1742L, Long.MAX_VALUE });
		});
		drone.assemblyInventory().setItem(0, markedMotor);
		drone.assemblyInventory().setItem(12, new ItemStack(DroneItems.fpvCamera()));
		CompoundTag saved = save(drone);
		assertTrue(saved.getDouble("assembly_schema") == 2.0 && saved.getString("assembly_items").startsWith("{"),
				"assembly save did not use the typed SNBT item format");
		DroneEntity restored = restoredDrone(helper.getLevel(), saved);
		assertTrue(restored.getAssemblyMask() == (1 | (1 << 12)) && !restored.isAssemblyComplete(), "new partial save was silently refilled");
		assertStack(markedMotor, restored.assemblyInventory().getItem(0), "saved ItemStack data components");
		assertTrue(restored.isOwnedBy(fixture.player().getUUID()), "assembly save lost ownership");
		CompoundTag legacy = saved.copy();
		legacy.remove("assembly_schema");
		legacy.remove("assembly_items");
		DroneEntity migrated = restoredDrone(helper.getLevel(), legacy);
		assertTrue(migrated.isAssemblyComplete() && migrated.getAssemblyMask() == DroneAssemblyInventory.FULL_MASK, "legacy entity did not receive the complete prototype assembly");
		CompoundTag corrupt = saved.copy();
		corrupt.putString("assembly_items", "{items:[]}");
		DroneEntity rejected = restoredDrone(helper.getLevel(), corrupt);
		assertTrue(rejected.assemblyInventory().isEmpty() && rejected.getAssemblyMask() == 0, "malformed new save generated replacement pieces");
		drone.discard();
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void fe_battery_menu_and_detached_save_preserve_charge_and_launch(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		DroneEntity drone = fixture.drone();
		Player player = fixture.player();
		DroneLoadout loadout = new DroneLoadout(DronePowerTier.ADVANCED, DroneRadioTier.ELITE, true);
		drone.configureMekanismLoadout(loadout);
		CustomData.update(DataComponents.CUSTOM_DATA, drone.assemblyInventory().getItem(11), tag -> tag.putString("battery_serial", "test-battery"));
		armDiagnostic(drone, player.getUUID());
		assertTrue(drone.isArmed(), "FE fixture never armed");
		disarm(drone, player.getUUID());
		drone.setPos(fixture.position().x(), fixture.position().y(), fixture.position().z());
		CompoundTag before = save(drone);
		long remaining = before.getLong("mek_energy_fe");
		assertTrue(remaining > 0L && remaining < loadout.powerTier().capacityFe(), "FE fixture did not consume real server-tick energy");
		DroneAssemblyMenu menu = new DroneAssemblyMenu(1, player.getInventory(), drone);
		menu.clicked(11, 0, ClickType.PICKUP, player);
		assertTrue(menu.getCarried().is(DroneItems.powerModule(DronePowerTier.ADVANCED)), "native pickup did not remove the FE module");
		assertTrue(Long.toString(remaining).equals(menu.getCarried().get(DataComponents.CUSTOM_DATA).copyTag().getString(ENERGY_KEY)), "native pickup lost the live battery charge");
		menu.clicked(13, 0, ClickType.PICKUP, player);
		assertTrue(menu.getCarried().isEmpty() && player.getInventory().getItem(9).getCount() == 1, "battery did not reach the backpack");
		assertTrue(!drone.hasMekanismLoadout(), "removing the power module kept FE flight enabled");
		menu.clicked(10, 0, ClickType.PICKUP, player);
		menu.clicked(14, 0, ClickType.PICKUP, player);
		player.getInventory().setItem(11, new ItemStack(DroneItems.radioModule(DroneRadioTier.ULTIMATE)));
		assertTrue(!menu.quickMoveStack(player, 15).isEmpty(), "replacement radio did not enter its native slot");
		CompoundTag detached = save(drone);
		assertTrue(!detached.getBoolean("mek_configured") && detached.getBoolean("mek_assembly_hardware_detached"), "detached hardware state was not persisted");
		DroneEntity restored = restoredDrone(helper.getLevel(), detached);
		restored.setPos(fixture.position().x(), fixture.position().y(), fixture.position().z());
		DroneAssemblyMenu reopened = new DroneAssemblyMenu(2, player.getInventory(), restored);
		assertTrue(!reopened.quickMoveStack(player, 13).isEmpty(), "saved detached aircraft did not accept its original battery");
		assertTrue(player.getInventory().getItem(9).isEmpty(), "reinstallation duplicated the original battery");
		CompoundTag reinstalled = save(restored);
		assertTrue(restored.isAssemblyComplete() && restored.hasMekanismLoadout(), "reinstalled aircraft did not regain its complete FE loadout");
		assertTrue(reinstalled.getLong("mek_energy_fe") == remaining, "removing and reloading refilled the FE battery");
		assertTrue(reinstalled.getBoolean("mek_quantum_link") && "ultimate".equals(reinstalled.getString("mek_radio_tier")), "detached reload lost the quantum flag or replacement radio");
		assertTrue(reinstalled.getDouble("mek_launch_x") == before.getDouble("mek_launch_x")
				&& reinstalled.getDouble("mek_launch_y") == before.getDouble("mek_launch_y")
				&& reinstalled.getDouble("mek_launch_z") == before.getDouble("mek_launch_z")
				&& reinstalled.getString("mek_launch_dimension").equals(before.getString("mek_launch_dimension")), "reinstallation reset the launch link");
		assertTrue("test-battery".equals(restored.assemblyInventory().getItem(11).get(DataComponents.CUSTOM_DATA).copyTag().getString("battery_serial")), "FE transfer erased unrelated battery data");
		CompoundTag legacy = before.copy();
		legacy.remove("assembly_schema");
		legacy.remove("assembly_items");
		DroneEntity migrated = restoredDrone(helper.getLevel(), legacy);
		assertTrue(migrated.isAssemblyComplete() && migrated.assemblyInventory().getItem(11).is(DroneItems.powerModule(DronePowerTier.ADVANCED))
				&& migrated.assemblyInventory().getItem(10).is(DroneItems.radioModule(DroneRadioTier.ELITE)), "legacy FE migration lost its actual hardware tiers");
		assertTrue(save(migrated).getLong("mek_energy_fe") == remaining, "legacy FE migration refilled stored energy");
		drone.discard();
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void lipo_battery_native_transfer_preserves_charge_and_fresh_replacement(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		Player player = fixture.player();
		DroneEntity source = fixture.drone();
		CompoundTag legacy = save(source);
		legacy.remove("assembly_schema");
		legacy.remove("assembly_items");
		legacy.putDouble("battery_amp_seconds_consumed", 1200.0);
		legacy.putDouble("battery_equivalent_cycles", 77.0);
		legacy.putDouble("battery_slow_polarization_v", 0.42);
		legacy.putDouble("battery_temp_c", 52.0);
		legacy.putDouble("battery_cooling_factor", 0.73);
		legacy.putDouble("battery_thermal_limit", 0.64);
		source.load(legacy);
		CustomData.update(DataComponents.CUSTOM_DATA, source.assemblyInventory().getItem(11), tag -> tag.putString("battery_serial", "lipo-a"));
		DroneEntity target = spawnDrone(helper, player.getUUID(), FlightModelMode.PLAYABLE);
		DroneAssemblyMenu sourceMenu = new DroneAssemblyMenu(1, player.getInventory(), source);
		DroneAssemblyMenu targetMenu = new DroneAssemblyMenu(2, player.getInventory(), target);
		sourceMenu.clicked(11, 0, ClickType.PICKUP, player);
		sourceMenu.clicked(13, 0, ClickType.PICKUP, player);
		assertTrue(player.getInventory().getItem(9).is(DroneItems.lipoBattery()), "native source pickup did not put LiPo A in the backpack");
		targetMenu.clicked(11, 0, ClickType.PICKUP, player);
		targetMenu.clicked(14, 0, ClickType.PICKUP, player);
		assertTrue(!targetMenu.quickMoveStack(player, 13).isEmpty(), "LiPo A did not enter the second aircraft");
		CompoundTag transferred = save(target);
		assertBatteryState(legacy, transferred, "physical LiPo transfer");
		assertTrue("lipo-a".equals(target.assemblyInventory().getItem(11).get(DataComponents.CUSTOM_DATA).copyTag().getString("battery_serial")), "LiPo transfer erased unrelated stack data");
		assertTrue(!target.hasMekanismLoadout(), "plain LiPo transfer incorrectly enabled FE flight");
		// A non-battery mutation must leave the installed cell's physical state untouched.
		targetMenu.clicked(10, 0, ClickType.PICKUP, player);
		targetMenu.clicked(15, 0, ClickType.PICKUP, player);
		player.getInventory().setItem(12, new ItemStack(DroneItems.radioModule(DroneRadioTier.ULTIMATE)));
		assertTrue(!targetMenu.quickMoveStack(player, 16).isEmpty(), "radio module did not enter the plain-LiPo aircraft");
		assertBatteryState(transferred, save(target), "receiver-only replacement");
		assertTrue(!target.hasMekanismLoadout(), "receiver-only replacement enabled FE without a power module");
		// New assembly saves make the cell item authoritative over stale legacy body fields.
		CompoundTag staleBody = save(target);
		staleBody.putDouble("battery_amp_seconds_consumed", 0.0);
		staleBody.putDouble("battery_equivalent_cycles", 0.0);
		staleBody.putDouble("battery_temp_c", 25.0);
		DroneEntity reopened = restoredDrone(helper.getLevel(), staleBody);
		assertBatteryState(transferred, save(reopened), "assembly item authority after reopen");
		// A genuinely new stack with no cell state must replace the aircraft's old depletion.
		targetMenu.clicked(11, 0, ClickType.PICKUP, player);
		targetMenu.clicked(17, 0, ClickType.PICKUP, player);
		player.getInventory().setItem(14, new ItemStack(DroneItems.lipoBattery()));
		assertTrue(!targetMenu.quickMoveStack(player, 18).isEmpty(), "new fresh LiPo was not installed");
		CompoundTag fresh = save(target);
		assertTrue(fresh.getDouble("battery_amp_seconds_consumed") == 0.0 && fresh.getDouble("battery_equivalent_cycles") == 0.0,
				"fresh cell inherited the previous battery's depletion or cycle count");
		assertTrue(fresh.getDouble("battery_temp_c") == 25.0 && fresh.getDouble("battery_slow_polarization_v") == 0.0,
				"fresh cell inherited the previous battery's thermal or polarization state");
		source.discard();
		target.discard();
		helper.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public static void destruction_drops_only_installed_real_components_once(GameTestHelper helper) {
		Fixture fixture = fixture(helper);
		DroneEntity drone = fixture.drone();
		drone.resetAssemblyToBareFrame();
		ItemStack markedMotor = new ItemStack(DroneItems.brushlessMotor());
		CustomData.update(DataComponents.CUSTOM_DATA, markedMotor, tag -> tag.putString("drop_serial", "unique-motor"));
		drone.assemblyInventory().setItem(0, markedMotor);
		drone.assemblyInventory().setItem(4, new ItemStack(DroneItems.propeller()));
		drone.assemblyInventory().setItem(12, new ItemStack(DroneItems.fpvCamera()));
		ItemStack battery = new ItemStack(DroneItems.powerModule(DronePowerTier.BASIC));
		CustomData.update(DataComponents.CUSTOM_DATA, battery, tag -> tag.putString(ENERGY_KEY, "12345"));
		drone.assemblyInventory().setItem(11, battery);
		drone.prepareAssemblyTransfer();
		List<ItemStack> expected = drone.assemblyInventory().contentsSnapshot();
		AABB area = drone.getBoundingBox().inflate(2.0);
		Set<UUID> existing = new HashSet<>();
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, area)) existing.add(item.getUUID());
		assertTrue(drone.hurt(helper.getLevel().damageSources().generic(), 1000.0f), "native damage did not destroy the frame");
		assertTrue(drone.isRemoved() && drone.assemblyInventory().isEmpty(), "destroyed entity retained a second component inventory");
		List<ItemStack> dropped = newDrops(helper.getLevel(), area, existing);
		assertTrue(dropped.stream().mapToInt(ItemStack::getCount).sum() == 4, "destruction generated missing parts or a duplicate whole aircraft");
		assertMultiset(expected, dropped, "native destruction drops");
		assertTrue(!drone.hurt(helper.getLevel().damageSources().generic(), 1000.0f), "removed frame accepted a second destructive hit");
		assertTrue(newDrops(helper.getLevel(), area, existing).stream().mapToInt(ItemStack::getCount).sum() == 4, "second destructive hit duplicated drops");
		DroneEntity bare = spawnDrone(helper, null, FlightModelMode.PLAYABLE);
		bare.resetAssemblyToBareFrame();
		bare.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
		assertTrue(newDrops(helper.getLevel(), area, existing).stream().mapToInt(ItemStack::getCount).sum() == 4, "destroying a bare frame created prototype components");
		helper.succeed();
	}

	private static Fixture fixture(GameTestHelper helper) {
		Player player = helper.makeMockPlayer(GameType.SURVIVAL);
		DroneEntity drone = spawnDrone(helper, player.getUUID(), FlightModelMode.PLAYABLE);
		placePlayer(player, drone.position());
		assertTrue(drone.canPlayerModifyAssembly(player), "real ground support did not permit assembly access");
		return new Fixture(drone, player, drone.position());
	}

	private static DroneEntity spawnDrone(GameTestHelper helper, UUID owner, FlightModelMode mode) {
		ServerLevel level = helper.getLevel();
		BlockPos support = helper.absolutePos(new BlockPos(1, 2, 1));
		level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
		FlightModelMode previous = DroneDebugSettings.flightModelMode();
		DroneEntity drone;
		try {
			DroneDebugSettings.setFlightModelMode(mode);
			drone = new DroneEntity(DroneEntityTypes.drone(), level);
		} finally {
			DroneDebugSettings.setFlightModelMode(previous);
		}
		drone.setOwner(owner);
		drone.setPos(support.getX() + 0.5, support.getY() + 1.04, support.getZ() + 0.5);
		level.addFreshEntity(drone);
		// A real placed drone sleeps above the surface and may never acquire native onGround.
		drone.tick();
		return drone;
	}

	private static void placePlayer(Player player, Vec3 position) {
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
		player.setPos(position.x() + 1.75, position.y(), position.z());
	}

	private static void assertMenuLocked(DroneAssemblyMenu menu, Player player, String reason) {
		assertTrue(!menu.stillValid(player), reason + " remained valid");
		ItemStack before = menu.assemblyInventory().getItem(0).copy();
		menu.clicked(0, 0, ClickType.PICKUP, player);
		assertTrue(menu.getCarried().isEmpty(), reason + " accepted a native pickup");
		assertTrue(menu.quickMoveStack(player, 0).isEmpty(), reason + " accepted a shift transfer");
		assertStack(before, menu.assemblyInventory().getItem(0), reason + " changed installed parts");
	}

	private static void armDiagnostic(DroneEntity drone, UUID owner) {
		DroneControlManager.startDiagnostic(owner, drone.getUUID(), drone.tickCount, 120, false, FlightMode.ANGLE);
		drone.tick();
	}

	private static void disarm(DroneEntity drone, UUID owner) {
		DroneControlManager.stopDiagnostic(owner);
		DroneControlManager.update(owner, drone.getUUID(), DISARMED, drone.tickCount);
		drone.tick();
		DroneControlManager.clearInput(owner);
		assertTrue(!drone.isArmed(), "server disarm input did not stop flight");
	}

	private static CompoundTag save(DroneEntity drone) { return drone.saveWithoutId(new CompoundTag()); }

	private static DroneEntity restoredDrone(ServerLevel level, CompoundTag tag) {
		DroneEntity restored = new DroneEntity(DroneEntityTypes.drone(), level);
		restored.load(tag);
		return restored;
	}

	private static int backpackCount(Player player) { return backpackStacks(player).stream().mapToInt(ItemStack::getCount).sum(); }

	private static List<ItemStack> backpackStacks(Player player) {
		List<ItemStack> result = new ArrayList<>();
		for (int slot = 0; slot < 36; slot++) if (!player.getInventory().getItem(slot).isEmpty()) result.add(player.getInventory().getItem(slot).copy());
		return result;
	}

	private static List<ItemStack> newDrops(ServerLevel level, AABB area, Set<UUID> existing) {
		return level.getEntitiesOfClass(ItemEntity.class, area).stream().filter(entity -> !existing.contains(entity.getUUID())).map(entity -> entity.getItem().copy()).toList();
	}

	private static void assertBatteryState(CompoundTag expected, CompoundTag actual, String message) {
		for (String field : List.of("battery_amp_seconds_consumed", "battery_equivalent_cycles", "battery_slow_polarization_v",
				"battery_temp_c", "battery_cooling_factor", "battery_thermal_limit")) {
			assertTrue(Math.abs(expected.getDouble(field) - actual.getDouble(field)) < 1.0e-9,
					message + " changed " + field + ": expected=" + expected.getDouble(field) + " actual=" + actual.getDouble(field));
		}
	}

	private static void assertMultiset(List<ItemStack> expected, List<ItemStack> actual, String message) {
		List<ItemStack> remaining = new ArrayList<>(actual.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy).toList());
		for (ItemStack stack : expected) {
			if (stack.isEmpty()) continue;
			int found = -1;
			for (int index = 0; index < remaining.size(); index++) {
				if (ItemStack.isSameItemSameComponents(stack, remaining.get(index)) && stack.getCount() <= remaining.get(index).getCount()) { found = index; break; }
			}
			assertTrue(found >= 0, message + " lost or changed " + stack);
			remaining.get(found).shrink(stack.getCount());
			if (remaining.get(found).isEmpty()) remaining.remove(found);
		}
		assertTrue(remaining.isEmpty(), message + " gained unexpected pieces " + remaining);
	}

	private static void assertStack(ItemStack expected, ItemStack actual, String message) {
		assertTrue(expected.getCount() == actual.getCount() && ItemStack.isSameItemSameComponents(expected, actual), message + ": expected=" + expected + " actual=" + actual);
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) throw new GameTestAssertException(message);
	}

	private record Fixture(DroneEntity drone, Player player, Vec3 position) { }
}
