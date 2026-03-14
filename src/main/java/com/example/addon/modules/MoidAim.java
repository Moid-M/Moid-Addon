package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MoidAim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgLegit = settings.createGroup("Legit / Smoothness");
    private final SettingGroup sgBypass = settings.createGroup("Bypass / Anti-Cheat");

    // --- General ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(4.5).min(1).sliderMax(10).build());
    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder().name("FOV").defaultValue(90.0).min(1).sliderMax(360).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    
    // --- Legit Modes ---
    public enum SmoothMode { Bezier, Inertia }
    private final Setting<SmoothMode> smoothMode = sgLegit.add(new EnumSetting.Builder<SmoothMode>().name("smooth-mode").defaultValue(SmoothMode.Bezier).build());
    private final Setting<Double> smoothing = sgLegit.add(new DoubleSetting.Builder().name("smoothing").defaultValue(0.15).min(0.01).sliderMax(1.0).build());
    private final Setting<Double> friction = sgLegit.add(new DoubleSetting.Builder().name("friction").description("Only for Inertia: How much the aim 'resists' movement.").defaultValue(0.1).min(0).sliderMax(0.5).visible(() -> smoothMode.get() == SmoothMode.Inertia).build());
    private final Setting<Boolean> lockCamera = sgLegit.add(new BoolSetting.Builder().name("lock-camera").defaultValue(true).build());

    // --- Bypass ---
    private final Setting<Boolean> packetLimit = sgBypass.add(new BoolSetting.Builder().name("packet-limiter").description("Prevents Timer flags by limiting rotations to 1 per tick.").defaultValue(true).build());
    private final Setting<Boolean> predict = sgBypass.add(new BoolSetting.Builder().name("predict").defaultValue(true).build());
    private final Setting<Double> bezierIntensity = sgBypass.add(new DoubleSetting.Builder().name("curve-intensity").defaultValue(0.2).min(0).sliderMax(1.0).build());

    private Entity target;
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private float velocityYaw, velocityPitch; // For Inertia mode
    private int packetCounter = 0;
    private double curveOffset;

    public MoidAim() {
        super(AddonTemplate.CATEGORY, "moid-aim", "V1.1: Added Inertia mode and Packet Limiter (Anti-Timer).");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            velocityYaw = 0;
            velocityPitch = 0;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        packetCounter = 0; // Reset packet count every tick
        target = findBestTarget();
        
        if (mc.player != null && mc.player.age % 5 == 0) {
            curveOffset = (random.nextDouble() - 0.5) * bezierIntensity.get();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target == null || mc.player == null) return;
        updateAimLogic(target, event.tickDelta);
    }

    private void updateAimLogic(Entity e, float tickDelta) {
        // 1. Get Target Pos (Universal Mapping Fix)
        double height = e.getEyeHeight(e.getPose()) * 0.6; 
        Vec3d targetVec = new Vec3d(e.getX(), e.getY() + height, e.getZ());

        // 2. Prediction
        if (predict.get()) {
            Vec3d vel = e.getVelocity();
            targetVec = targetVec.add(vel.x * 2.0, vel.y * 2.0, vel.z * 2.0);
        }

        float targetYaw = (float) Rotations.getYaw(targetVec);
        float targetPitch = (float) Rotations.getPitch(targetVec);

        // 3. Choice of Smoothing Mode
        if (smoothMode.get() == SmoothMode.Bezier) {
            // Bezier Pathing (Curvy)
            double time = System.currentTimeMillis() * 0.002;
            targetYaw += (float) (Math.sin(time) * curveOffset * 10);
            targetPitch += (float) (Math.cos(time) * curveOffset * 5);
            
            float accel = (float) Math.min(1.0, smoothing.get() * (tickDelta + 0.5f));
            lastYaw += MathHelper.wrapDegrees(targetYaw - lastYaw) * accel;
            lastPitch += (targetPitch - lastPitch) * accel;

        } else if (smoothMode.get() == SmoothMode.Inertia) {
            // Inertia Mode (Heavy/Weighted mouse)
            float diffYaw = MathHelper.wrapDegrees(targetYaw - lastYaw);
            float diffPitch = targetPitch - lastPitch;

            // Apply 'force' to the velocity
            velocityYaw += diffYaw * (smoothing.get() * 0.1f);
            velocityPitch += diffPitch * (smoothing.get() * 0.1f);

            // Apply friction (air resistance)
            velocityYaw *= (1.0f - friction.get());
            velocityPitch *= (1.0f - friction.get());

            lastYaw += velocityYaw;
            lastPitch += velocityPitch;
        }

        // 4. Packet Limiter (The Timer Bypass)
        if (lockCamera.get()) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
        }

        if (!packetLimit.get() || packetCounter < 1) {
            Rotations.rotate(lastYaw, lastPitch);
            packetCounter++;
        }
    }

    private Entity findBestTarget() {
        Entity best = null;
        double bestScore = Double.MAX_VALUE;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (!entities.get().contains(e.getType())) continue;
            if (mc.player.distanceTo(e) > range.get()) continue;
            if (!mc.player.canSee(e)) continue;

            float angle = (float) Math.abs(MathHelper.wrapDegrees(Rotations.getYaw(e.getBoundingBox().getCenter()) - mc.player.getYaw()));
            if (angle > fov.get() / 2) continue;

            if (angle < bestScore) {
                bestScore = angle;
                best = e;
            }
        }
        return best;
    }
}