package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

public class GrimPivot extends Module {

    public GrimPivot() {
        super(AddonTemplate.CATEGORY, "grim-pivot", "Exploits rotation-based momentum to bypass simulation speed caps.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || !mc.player.isOnGround()) return;

        boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();

        if (isMoving) {
            Vec3d vel = mc.player.getVelocity();
            
            // Instead of multiplying, we 'Zero-Out' the minor drag vectors.
            // This makes your movement perfectly linear, which can trick the 
            // friction calculation into skipping a decay cycle.
            double optimizedX = (Math.abs(vel.x) < 0.001) ? 0 : vel.x;
            double optimizedZ = (Math.abs(vel.z) < 0.001) ? 0 : vel.z;

            // Apply a tiny 'Flicker' to the velocity. 
            // Some Grim versions ignore movement under 0.005 if it alternates.
            if (mc.player.age % 2 == 0) {
                mc.player.setVelocity(optimizedX * 1.005, vel.y, optimizedZ * 1.005);
            }
        }
    }
}
