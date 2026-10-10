package com.tenicana.dronecraft.command;
import java.util.function.Predicate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
final class DroneCommandPermissions {
    private DroneCommandPermissions() { }
    static Predicate<CommandSourceStack> gamemasterOnly() {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
    }
}
