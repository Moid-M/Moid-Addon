package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.Vec3d;

public class VulcanWeb extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> webSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("web-speed")
        .description("Movement efficiency in webs (0.1 is vanilla, 1.0 is full speed).")
        .defaultValue(0.45)
        .min(0.1)
        .sliderMax(1.0)
        .build()
    );

    public VulcanWeb() {
        super(AddonTemplate.CATEGORY, "vulcan-web", "Customizable Web NoSlow bypass for Vulcan.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        // Check if the player is in a web (feet or head)
        boolean inWeb = mc.world.getBlockState(mc.player.getBlockPos()).getBlock() == Blocks.COBWEB 
                     || mc.world.getBlockState(mc.player.getBlockPos().up()).getBlock() == Blocks.COBWEB;

        if (inWeb) {
            // Apply the custom speed factor from the slider
            double s = webSpeed.get();
            mc.player.slowMovement(mc.world.getBlockState(mc.player.getBlockPos()), new Vec3d(s, s, s));
        }
    }
}
