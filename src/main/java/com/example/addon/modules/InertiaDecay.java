package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

public class InertiaDecay extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> multiplier = sgGeneral.add(new DoubleSetting.Builder()
        .name("multiplier")
        .description("The amount to counteract friction. 1.01 is usually the sweet spot for Grim.")
        .defaultValue(1.012)
        .min(1.0)
        .max(1.05)
        .sliderMax(1.05)
        .build()
    );

    public InertiaDecay() {
        super(AddonTemplate.CATEGORY, "inertia-decay", "Artificially reduces velocity decay to maintain momentum.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        // Mapping-safe movement check using game options
        boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();

        // Ensure the player is actively moving to avoid 'sliding' flags while standing still
        if (mc.player.isOnGround() && isMoving) {
            Vec3d vel = mc.player.getVelocity();
            
            // Only apply to X and Z to avoid messing with gravity/jump simulation
            double nextX = vel.x * multiplier.get();
            double nextZ = vel.z * multiplier.get();

            mc.player.setVelocity(nextX, vel.y, nextZ);
        }
    }
}
