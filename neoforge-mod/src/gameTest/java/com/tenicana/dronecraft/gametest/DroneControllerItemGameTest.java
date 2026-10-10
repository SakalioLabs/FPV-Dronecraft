package com.tenicana.dronecraft.gametest;

import com.tenicana.dronecraft.item.DroneControllerItem;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTest;

import com.tenicana.dronecraft.entity.DroneEntity;
import com.tenicana.dronecraft.control.DroneControlManager;
import com.tenicana.dronecraft.registry.DroneEntityTypes;
import com.tenicana.dronecraft.registry.DroneItems;

@GameTestHolder("fpvdrone")
@PrefixGameTestTemplate(false)
public final class DroneControllerItemGameTest {
	private static final UUID OTHER_OWNER = UUID.fromString("00000000-0000-0000-0000-00000000c002");

@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void survivalPlacementConsumesOneAndStartsUnbound(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
		Player player = placementPlayer(context, GameType.SURVIVAL, support);
		player.setYRot(37.0f);
		ItemStack stack = new ItemStack(DroneItems.drone(), 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		InteractionResult result = stack.useOn(placementContext(player, support));
		assertTrue(result.consumesAction(), "survival placement failed");
		assertTrue(stack.getCount() == 1, "successful survival placement did not consume exactly one item");
		DroneEntity drone = onlyDrone(level, support);
		assertTrue(!drone.hasOwner() && !drone.isArmed(), "placed drone was already bound or armed");
		assertTrue(drone.isPickable(), "placed drone cannot be targeted by native player interaction");
		assertTrue(Math.abs(drone.getY() - (support.getY() + 1.04)) < 1.0e-6, "drone was not placed above the support surface");
		assertTrue(Math.abs(drone.getYRot() - 37.0f) < 1.0e-6, "placement did not preserve the player's yaw");
		drone.discard();
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void creativePlacementKeepsItemAndSupportsSlabs(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		level.setBlock(support, Blocks.STONE_SLAB.defaultBlockState(), 3);
		Player player = placementPlayer(context, GameType.CREATIVE, support);
		ItemStack stack = new ItemStack(DroneItems.drone());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		assertTrue(stack.useOn(placementContext(player, support)).consumesAction(), "creative slab placement failed");
		assertTrue(stack.getCount() == 1, "creative placement consumed its item");
		DroneEntity drone = onlyDrone(level, support);
		assertTrue(Math.abs(drone.getY() - (support.getY() + 0.54)) < 1.0e-6, "slab placement ignored the actual collision height");
		drone.discard();
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void blockedOrUnsupportedPlacementDoesNotConsumeOrSpawn(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		Player player = placementPlayer(context, GameType.SURVIVAL, support);
		ItemStack stack = new ItemStack(DroneItems.drone());
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		level.setBlock(support, Blocks.STONE.defaultBlockState(), 3);
		level.setBlock(support.above(), Blocks.STONE.defaultBlockState(), 3);
		assertTrue(stack.useOn(placementContext(player, support)) == InteractionResult.FAIL, "blocked placement succeeded");
		assertTrue(stack.getCount() == 1 && droneCount(level, support) == 0, "blocked placement consumed or created a drone");
		level.setBlock(support.above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(support, Blocks.AIR.defaultBlockState(), 3);
		assertTrue(stack.useOn(placementContext(player, support)) == InteractionResult.FAIL, "unsupported placement succeeded");
		assertTrue(stack.getCount() == 1 && droneCount(level, support) == 0, "unsupported placement consumed or created a drone");
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void controllerUseAndPlainInteractionNeverCreateBindOrRepair(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		Player player = placementPlayer(context, GameType.CREATIVE, support);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.droneController()));
		DroneItems.droneController().use(level, player, InteractionHand.MAIN_HAND);
		assertTrue(droneCount(level, support) == 0, "controller right-click still generated a drone");
		DroneEntity drone = spawnDrone(level, null, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
		drone.hurt(level.damageSources().generic(), 2.0f);
		float health = drone.getFrameHealth();
		player.setShiftKeyDown(false);
		drone.interact(player, InteractionHand.MAIN_HAND);
		assertTrue(!drone.hasOwner() && DroneControllerItem.boundDroneId(player) == null, "plain right-click unexpectedly bound a drone");
		assertTrue(drone.getFrameHealth() == health, "plain right-click repaired damage");
		DroneItems.droneController().use(level, player, InteractionHand.MAIN_HAND);
		assertTrue(droneCount(level, support) == 1 && !drone.hasOwner(), "controller reused or created a drone");
		drone.discard();
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void shiftBindingSelectsExactDroneKeepsDamageAndOtherData(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		Player player = placementPlayer(context, GameType.CREATIVE, support);
		ItemStack controller = new ItemStack(DroneItems.droneController());
		CustomData.update(DataComponents.CUSTOM_DATA, controller, tag -> tag.putString("external_marker", "keep-me"));
		player.setItemInHand(InteractionHand.MAIN_HAND, controller);
		player.setShiftKeyDown(true);
		Vec3 origin = Vec3.atCenterOf(support).add(0.0, 0.5, 0.0);
		DroneEntity first = spawnDrone(level, null, origin);
		DroneEntity second = spawnDrone(level, player.getUUID(), origin.add(0.0, 0.0, 1.2));
		first.hurt(level.damageSources().generic(), 2.0f);
		float health = first.getFrameHealth();
		assertTrue(first.interact(player, InteractionHand.MAIN_HAND).consumesAction(), "Shift entity interaction failed to bind");
		assertTrue(first.isOwnedBy(player.getUUID()) && first.getUUID().equals(DroneControllerItem.boundDroneId(controller)), "binding did not record the exact entity UUID");
		assertTrue(first.getFrameHealth() == health, "Shift binding repaired damaged drone");
		assertTrue("keep-me".equals(controller.get(DataComponents.CUSTOM_DATA).copyTag().getString("external_marker")), "binding erased unrelated custom data");
		assertTrue(DroneControllerItem.bindToDrone(player, InteractionHand.MAIN_HAND, second), "owned drone could not be selected explicitly");
		assertTrue(second.getUUID().equals(DroneControllerItem.boundDroneId(player)), "binding selected a nearby drone instead of the pointed drone");
		player.setItemInHand(InteractionHand.OFF_HAND, controller.copy());
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.droneController()));
		assertTrue(DroneControllerItem.hasControllerItem(player) && DroneControllerItem.boundDroneId(player) == null, "unbound main-hand controller incorrectly fell back to offhand");
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		assertTrue(second.getUUID().equals(DroneControllerItem.boundDroneId(player)), "offhand binding was not available when main hand had no controller");
		CustomData.update(DataComponents.CUSTOM_DATA, player.getOffhandItem(), tag -> tag.putString("fpvdrone_bound_drone", "bad UUID"));
		assertTrue(DroneControllerItem.boundDroneId(player) == null, "malformed controller UUID was accepted");
		first.discard();
		second.discard();
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void bindingRejectsOtherOwnerRemovedAndOutOfReach(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		Player player = placementPlayer(context, GameType.CREATIVE, support);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.droneController()));
		player.setShiftKeyDown(true);
		DroneEntity drone = spawnDrone(level, OTHER_OWNER, Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
		assertTrue(drone.interact(player, InteractionHand.MAIN_HAND) == InteractionResult.FAIL, "another player's drone was stolen");
		assertTrue(drone.isOwnedBy(OTHER_OWNER) && DroneControllerItem.boundDroneId(player) == null, "rejected binding changed the owner or stack");
		drone.setOwner(null);
		player.setPos(player.getX() + 20.0, player.getY(), player.getZ());
		assertTrue(!DroneControllerItem.bindToDrone(player, InteractionHand.MAIN_HAND, drone), "out-of-reach binding was accepted");
		player.setPos(support.getX() + 2.25, support.getY() + 1.0, support.getZ() + 0.5);
		drone.discard();
		assertTrue(!DroneControllerItem.bindToDrone(player, InteractionHand.MAIN_HAND, drone), "removed drone could be bound");
		context.succeed();
	}

	@GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
	public void armedDroneCannotBeRebound(GameTestHelper context) {
		ServerLevel level = context.getLevel();
		BlockPos support = context.absolutePos(new BlockPos(1, 2, 1));
		Player player = placementPlayer(context, GameType.CREATIVE, support);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneItems.droneController()));
		player.setShiftKeyDown(true);
		DroneEntity drone = spawnDrone(level, player.getUUID(), Vec3.atCenterOf(support).add(0.0, 0.5, 0.0));
		DroneControlManager.startDiagnostic(player.getUUID(), drone.tickCount, 120);
		drone.tick();
		assertTrue(drone.isArmed(), "armed binding fixture did not run its diagnostic input");
		assertTrue(!DroneControllerItem.bindToDrone(player, InteractionHand.MAIN_HAND, drone), "armed drone accepted binding");
		assertTrue(DroneControllerItem.boundDroneId(player) == null, "armed rejection changed controller UUID");
		DroneControlManager.stopDiagnostic(player.getUUID());
		DroneControlManager.clearInput(player.getUUID());
		drone.discard();
		context.succeed();
	}

	private static Player placementPlayer(GameTestHelper context, GameType type, BlockPos support) {
		Player player = context.makeMockPlayer(type);
		// The native mock only overrides gameMode(); a real game-mode change also updates these abilities.
		type.updatePlayerAbilities(player.getAbilities());
		player.setPos(support.getX() + 2.25, support.getY() + 1.0, support.getZ() + 0.5);
		return player;
	}

	private static UseOnContext placementContext(Player player, BlockPos support) {
		return new UseOnContext(player, InteractionHand.MAIN_HAND,
				new BlockHitResult(Vec3.atCenterOf(support).add(0.0, 0.5, 0.0), Direction.UP, support, false));
	}

	private static DroneEntity onlyDrone(ServerLevel level, BlockPos support) {
		var drones = level.getEntitiesOfClass(DroneEntity.class, new AABB(support).inflate(2.0));
		assertTrue(drones.size() == 1, "expected one placed drone, got " + drones.size());
		return drones.getFirst();
	}

	private static int droneCount(ServerLevel level, BlockPos support) {
		return level.getEntitiesOfClass(DroneEntity.class, new AABB(support).inflate(3.0)).size();
	}

	private static DroneEntity spawnDrone(ServerLevel level, UUID owner, Vec3 position) {
		DroneEntity drone = new DroneEntity(DroneEntityTypes.drone(), level);
		drone.setOwner(owner);
		drone.setPos(position.x(), position.y(), position.z());
		level.addFreshEntity(drone);
		return drone;
	}

	private static void assertTrue(boolean condition, String message) {
		if (!condition) {
			throw new GameTestAssertException(message);
		}
	}
}
