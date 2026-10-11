package com.tenicana.dronecraft.registry;

import com.tenicana.dronecraft.FpvDronecraftMod;
import com.tenicana.dronecraft.menu.DroneAssemblyMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class DroneMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FpvDronecraftMod.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<DroneAssemblyMenu>> ASSEMBLY = MENUS.register(
            "drone_assembly", () -> IMenuTypeExtension.create(DroneAssemblyMenu::new));

    private DroneMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
