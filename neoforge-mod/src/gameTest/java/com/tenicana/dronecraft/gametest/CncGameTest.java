package com.tenicana.dronecraft.gametest;

import java.util.List;

import com.tenicana.dronecraft.machine.CncWorkstationBlock;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import com.tenicana.dronecraft.menu.CncWorkstationMenu;
import com.tenicana.dronecraft.registry.DroneBlocks;
import com.tenicana.dronecraft.registry.DroneItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("fpvdrone")
@PrefixGameTestTemplate(false)
public final class CncGameTest {
    private CncGameTest() { }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_four_plates_complete_atomically_after_120_ticks(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        CncWorkstationBlockEntity machine = fixture.machine();
        machine.setItem(0, new ItemStack(DroneItems.carbonPlate(), 8));
        ticks(fixture, 119);
        require(machine.getProgress() == 119 && machine.getItem(0).getCount() == 8 && machine.getItem(1).isEmpty(),
                "CNC consumed material or completed before its 120th native ticker call");
        ticks(fixture, 1);
        require(machine.getItem(0).getCount() == 4 && machine.getItem(1).is(DroneItems.carbonFrame())
                && machine.getItem(1).getCount() == 1 && machine.getProgress() == 0,
                "CNC completion did not atomically exchange exactly four plates for one existing frame");
        ticks(fixture, 240);
        require(machine.getItem(0).getCount() == 4 && machine.getItem(1).getCount() == 1 && !machine.isProcessing(),
                "A blocked output consumed another batch");
        ItemStack firstFrame = machine.removeItem(1, 1);
        ticks(fixture, 120);
        require(firstFrame.is(DroneItems.carbonFrame()) && machine.getItem(0).isEmpty()
                && machine.getItem(1).is(DroneItems.carbonFrame()) && machine.getItem(1).getCount() == 1,
                "Output extraction did not release exactly the remaining four-plate batch");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_wrong_or_insufficient_material_never_processes(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        CncWorkstationBlockEntity machine = fixture.machine();
        for (ItemStack input : List.of(ItemStack.EMPTY, new ItemStack(DroneItems.carbonPlate(), 3), new ItemStack(Items.DIRT, 4))) {
            machine.setItem(0, input.copy());
            ticks(fixture, 160);
            require(ItemStack.matches(input, machine.getItem(0)) && machine.getItem(1).isEmpty()
                    && machine.getProgress() == 0 && !machine.isProcessing(), "Invalid or insufficient material started a CNC job");
        }
        require(!machine.canPlaceItem(0, new ItemStack(Items.DIRT))
                && !machine.canPlaceItem(1, new ItemStack(DroneItems.carbonFrame())), "Native insertion accepted a wrong input or output insertion");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_input_changes_cancel_progress_without_loss(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        CncWorkstationBlockEntity machine = fixture.machine();
        machine.setItem(0, new ItemStack(DroneItems.carbonPlate(), 4));
        ticks(fixture, 60);
        ItemStack withdrawn = machine.removeItem(0, 1);
        require(withdrawn.getCount() == 1 && machine.getItem(0).getCount() == 3 && machine.getProgress() == 0,
                "Input removal consumed unfinished material or retained old progress");
        ticks(fixture, 120);
        require(machine.getItem(0).getCount() == 3 && machine.getItem(1).isEmpty(), "Cancelled partial input still produced a frame");
        machine.setItem(0, new ItemStack(DroneItems.carbonPlate(), 4));
        ticks(fixture, 40);
        machine.getItem(0).grow(1);
        machine.setChanged();
        require(machine.getProgress() == 0 && machine.getItem(0).getCount() == 5,
                "Native live-stack insertion did not restart the changed workpiece");
        ticks(fixture, 40);
        CustomData.update(DataComponents.CUSTOM_DATA, machine.getItem(0), tag -> tag.putInt("batch", 7));
        machine.setChanged();
        require(machine.getProgress() == 0 && machine.getItem(0).getCount() == 5,
                "Changing input components retained progress or deleted material");
        ticks(fixture, 120);
        require(machine.getItem(0).getCount() == 1 && machine.getItem(1).is(DroneItems.carbonFrame()),
                "Restarted native CNC job did not consume exactly four plates");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_blocked_output_never_overwrites_or_consumes(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        CncWorkstationBlockEntity machine = fixture.machine();
        machine.setItem(0, new ItemStack(DroneItems.carbonPlate(), 4));
        ticks(fixture, 80);
        ItemStack blocked = namedStack(Items.DIRT, 1, "preserved obstruction");
        machine.setItem(1, blocked.copy());
        ticks(fixture, 240);
        require(ItemStack.matches(blocked, machine.getItem(1)) && machine.getItem(0).getCount() == 4
                && machine.getProgress() == 0, "Blocked output was overwritten or consumed input");
        machine.removeItem(1, 1);
        ticks(fixture, 119);
        require(machine.getItem(0).getCount() == 4 && machine.getItem(1).isEmpty(), "Cleared obstruction resumed cancelled progress");
        ticks(fixture, 1);
        require(machine.getItem(0).isEmpty() && machine.getItem(1).is(DroneItems.carbonFrame()), "Cleared CNC failed to complete its new full job");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_menu_transfers_preserve_full_backpacks(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(fixture.position().getX() + 0.5, fixture.position().getY() + 0.5, fixture.position().getZ() + 0.5);
        CncWorkstationMenu menu = new CncWorkstationMenu(1, player.getInventory(), fixture.machine());
        CncWorkstationMenu concurrent = new CncWorkstationMenu(2, player.getInventory(), fixture.machine());
        require(menu.stillValid(player) && menu.slots.size() == 38, "Native CNC menu did not expose two machine slots and the real backpack");
        player.getInventory().setItem(9, new ItemStack(DroneItems.carbonPlate(), 8));
        require(!menu.quickMoveStack(player, 2).isEmpty() && player.getInventory().getItem(9).isEmpty()
                && fixture.machine().getItem(0).getCount() == 8, "Native shift transfer did not move plates into the machine");
        require(!menu.getSlot(0).mayPlace(new ItemStack(Items.DIRT)) && !menu.getSlot(1).mayPlace(new ItemStack(DroneItems.carbonPlate())),
                "CNC native slot predicates accepted invalid insertion");
        player.getInventory().setItem(10, new ItemStack(Items.DIRT, 12));
        menu.clicked(3, 0, ClickType.PICKUP, player);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        require(fixture.machine().getItem(0).getCount() == 8 && menu.getCarried().is(Items.DIRT) && menu.getCarried().getCount() == 12,
                "Native click replaced input with an unrelated item");
        menu.clicked(3, 0, ClickType.PICKUP, player);
        ticks(fixture, 120);
        for (int slot = 0; slot < 36; slot++) player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        ItemStack output = fixture.machine().getItem(1).copy();
        require(menu.quickMoveStack(player, 1).isEmpty() && ItemStack.matches(output, fixture.machine().getItem(1)),
                "Full backpack deleted or duplicated a CNC output");
        player.getInventory().setItem(0, ItemStack.EMPTY);
        require(!menu.quickMoveStack(player, 1).isEmpty() && concurrent.quickMoveStack(player, 1).isEmpty()
                && player.getInventory().getItem(0).is(DroneItems.carbonFrame()), "Concurrent native CNC menus duplicated output");
        player.setPos(fixture.position().getX() + 20, fixture.position().getY(), fixture.position().getZ());
        require(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Out-of-reach menu still transferred machine contents");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_save_restores_progress_and_typed_components(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemStack input = namedStack(DroneItems.carbonPlate(), 6, "persistent CNC batch");
        fixture.machine().setItem(0, input.copy());
        ticks(fixture, 57);
        CompoundTag saved = fixture.machine().saveWithFullMetadata(fixture.level().registryAccess());
        BlockState state = fixture.level().getBlockState(fixture.position());
        BlockEntity restored = BlockEntity.loadStatic(fixture.position(), state, saved, fixture.level().registryAccess());
        require(restored instanceof CncWorkstationBlockEntity, "Native BlockEntity loader did not resolve the registered CNC type");
        CncWorkstationBlockEntity machine = (CncWorkstationBlockEntity) restored;
        fixture.level().setBlockEntity(machine);
        Fixture reopened = new Fixture(fixture.level(), fixture.position(), machine, fixture.ticker());
        require(machine.getProgress() == 57 && ItemStack.matches(input, machine.getItem(0)) && machine.getItem(1).isEmpty(),
                "Native CNC save lost progress, item components, or numeric NBT types");
        ticks(reopened, 62);
        require(machine.getProgress() == 119 && machine.getItem(0).getCount() == 6 && machine.getItem(1).isEmpty(),
                "Reloaded CNC restarted or consumed its unfinished batch");
        ticks(reopened, 1);
        ItemStack remaining = input.copy(); remaining.setCount(2);
        require(ItemStack.matches(remaining, machine.getItem(0)) && machine.getItem(1).is(DroneItems.carbonFrame()),
                "Reloaded completion did not preserve remaining real item components");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "fpvdrone", timeoutTicks = 60)
    public static void native_cnc_removal_drops_inventory_once_and_facing_keeps_it(GameTestHelper helper) {
        Fixture fixture = fixture(helper);
        ItemStack input = namedStack(DroneItems.carbonPlate(), 7, "real remaining plates");
        ItemStack output = namedStack(DroneItems.carbonFrame(), 1, "real finished frame");
        fixture.machine().setItem(0, input.copy());
        fixture.machine().setItem(1, output.copy());
        fixture.level().setBlock(fixture.position(), fixture.level().getBlockState(fixture.position()).setValue(CncWorkstationBlock.FACING, Direction.EAST), 3);
        require(fixture.level().getBlockEntity(fixture.position()) == fixture.machine() && drops(fixture).isEmpty()
                && ItemStack.matches(input, fixture.machine().getItem(0)), "Changing CNC facing deleted its block entity or dropped live contents");
        fixture.level().setBlock(fixture.position(), Blocks.AIR.defaultBlockState(), 3);
        require(fixture.level().getBlockEntity(fixture.position()) == null && fixture.machine().isEmpty(),
                "Native removal failed to drain and remove the CNC block entity");
        List<ItemEntity> dropped = drops(fixture);
        require(dropped.stream().filter(entity -> entity.getItem().is(DroneItems.carbonPlate())).mapToInt(entity -> entity.getItem().getCount()).sum() == 7
                && dropped.stream().filter(entity -> entity.getItem().is(DroneItems.carbonFrame())).mapToInt(entity -> entity.getItem().getCount()).sum() == 1,
                "Native destruction lost or duplicated real CNC contents");
        for (ItemEntity entity : dropped) {
            ItemStack expected = entity.getItem().is(DroneItems.carbonPlate()) ? input.copy() : output.copy();
            expected.setCount(entity.getItem().getCount());
            require(ItemStack.matches(expected, entity.getItem()), "Native CNC drops lost custom data components");
        }
        fixture.machine().dropContentsOnce();
        require(drops(fixture).stream().mapToInt(entity -> entity.getItem().getCount()).sum() == 8,
                "A repeated removal callback duplicated machine contents");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState state = DroneBlocks.cncWorkstation().defaultBlockState();
        level.setBlock(position, state, 3);
        require(level.getBlockEntity(position) instanceof CncWorkstationBlockEntity, "Native placement did not create the registered CNC block entity");
        CncWorkstationBlockEntity machine = (CncWorkstationBlockEntity) level.getBlockEntity(position);
        BlockEntityTicker<CncWorkstationBlockEntity> ticker = DroneBlocks.CNC_WORKSTATION.get().getTicker(level, state, DroneBlocks.CNC_WORKSTATION_ENTITY.get());
        require(ticker != null, "Native CNC block did not register its server ticker");
        return new Fixture(level, position, machine, ticker);
    }

    private static void ticks(Fixture fixture, int count) {
        for (int tick = 0; tick < count; tick++) fixture.ticker().tick(fixture.level(), fixture.position(), fixture.level().getBlockState(fixture.position()), fixture.machine());
    }
    private static List<ItemEntity> drops(Fixture fixture) { return fixture.level().getEntitiesOfClass(ItemEntity.class, new AABB(fixture.position()).inflate(2.0)); }
    private static ItemStack namedStack(net.minecraft.world.item.Item item, int count, String name) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        CompoundTag data = new CompoundTag();
        data.putInt("serial", 1742); data.putLong("large_serial", 9007199254740993L); data.putByte("typed_byte", (byte) 7);
        data.putIntArray("typed_array", new int[]{1, 1742, -7});
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        return stack;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new GameTestAssertException(message); }
    private record Fixture(ServerLevel level, BlockPos position, CncWorkstationBlockEntity machine, BlockEntityTicker<CncWorkstationBlockEntity> ticker) { }
}
