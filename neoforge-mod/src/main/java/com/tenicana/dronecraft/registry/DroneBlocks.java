package com.tenicana.dronecraft.registry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.machine.CncWorkstationBlock;
import com.tenicana.dronecraft.machine.CncWorkstationBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneBlocks {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FpvDronecraftMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FpvDronecraftMod.MOD_ID);
    public static final DeferredBlock<CncWorkstationBlock> CNC_WORKSTATION = BLOCKS.registerBlock(
            "cnc_workstation", properties -> new CncWorkstationBlock(properties.strength(3.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CncWorkstationBlockEntity>> CNC_WORKSTATION_ENTITY = BLOCK_ENTITIES.register(
            "cnc_workstation", () -> BlockEntityType.Builder.of(CncWorkstationBlockEntity::new, CNC_WORKSTATION.get()).build(null));
    private DroneBlocks() { }
    public static Block cncWorkstation() { return CNC_WORKSTATION.get(); }
    public static void register(IEventBus bus) { BLOCKS.register(bus); BLOCK_ENTITIES.register(bus); }
}
