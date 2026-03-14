package com.example.addon.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.command.CommandSource;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class PanicCommand extends Command {
    public PanicCommand() {
        super("panic", "Disables all active modules instantly.", "disable-all", "terminate");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            // Get all modules and turn off the ones that are active
            int count = 0;
            for (Module module : Modules.get().getActive()) {
                module.toggle();
                count++;
            }

            info("Panic triggered! Disabled " + count + " modules.");
            return SINGLE_SUCCESS;
        });
    }
}