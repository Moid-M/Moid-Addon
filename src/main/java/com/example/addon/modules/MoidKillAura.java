package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MoidKillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRotations = settings.createGroup("Bypass Rotations");
    private final SettingGroup sgSwing = settings.createGroup("Clicker (Anti-AutoClicker)");
    private final SettingGroup sgBypass = settings.createGroup("Anti-Cheat (Vulcan/Grim)");
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    // --- General & AutoWalk ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("attack-range").defaultValue(3.0).min(1).sliderMax(6).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Boolean> autoWalk = sgGeneral.add(new BoolSetting.Builder().name("auto-walk").defaultValue(false).build());
    private final Setting<Double> walkStopRange = sgGeneral.add(new DoubleSetting.Builder().name("stop-range").defaultValue(1.5).min(0).sliderMax(6).visible(autoWalk::get).build());
    private final Setting<Boolean> walkInside = sgGeneral.add(new BoolSetting.Builder().name("walk-inside").defaultValue(false).visible(autoWalk::get).build());

    // --- Advanced Rotations ---
    private final Setting<Double> smoothness = sgRotations.add(new DoubleSetting.Builder().name("smoothness").defaultValue(0.12).min(0.01).sliderMax(1.0).build());
    public enum AimPart { Head, Chest, Feet, Random }
    private final Setting<AimPart> aimPart = sgRotations.add(new EnumSetting.Builder<AimPart>().name("aim-part").defaultValue(AimPart.Chest).build());
    private final Setting<Double> curveIntensity = sgRotations.add(new DoubleSetting.Builder().name("curve-intensity").defaultValue(0.15).min(0).sliderMax(1.0).build());
    private final Setting<Double> humanJitter = sgRotations.add(new DoubleSetting.Builder().name("human-jitter").defaultValue(0.05).min(0).sliderMax(0.5).build());
    private final Setting<Boolean> lockCamera = sgRotations.add(new BoolSetting.Builder().name("lock-camera").defaultValue(false).build());

    // --- Clicker Modes ---
    public enum SwingMode { Dynamic, Static, Randomized }
    private final Setting<SwingMode> swingMode = sgSwing.add(new EnumSetting.Builder<SwingMode>().name("swing-mode").defaultValue(SwingMode.Dynamic).build());
    private final Setting<Integer> minCps = sgSwing.add(new IntSetting.Builder().name("min-cps").defaultValue(8).min(1).max(20).visible(() -> swingMode.get() != SwingMode.Dynamic).build());
    private final Setting<Integer> maxCps = sgSwing.add(new IntSetting.Builder().name("max-cps").defaultValue(12).min(1).max(20).visible(() -> swingMode.get() == SwingMode.Randomized).build());

    // --- Bypass ---
    private final Setting<Boolean> packetLimit = sgBypass.add(new BoolSetting.Builder().name("packet-limiter").defaultValue(true).build());
    private final Setting<Boolean> raycast = sgBypass.add(new BoolSetting.Builder().name("raycast").defaultValue(true).build());
    private final Setting<Double> maxAngle = sgBypass.add(new DoubleSetting.Builder().name("max-angle").defaultValue(10.0).min(1).sliderMax(45).build());

    // --- Visuals ---
    private final Setting<Boolean> renderTarget = sgVisuals.add(new BoolSetting.Builder().name("render-target").defaultValue(true).build());
    private final Setting<SettingColor> sideColor = sgVisuals.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 0, 0, 75)).build());
    private final Setting<SettingColor> lineColor = sgVisuals.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());

    private Entity target;
    private final List<Entity> targets = new ArrayList<>();
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private long lastAttackTime;
    private int packetCounter = 0;

    public MoidKillAura() {
        super(AddonTemplate.CATEGORY, "moid-kill-aura", "V9.4: Fixed getPos mapping error.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        findTargets();
        if (targets.isEmpty()) {
            target = null;
            if (autoWalk.get()) mc.options.forwardKey.setPressed(false);
            return;
        }

        target = targets.get(0);
        packetCounter = 0; 

        if (autoWalk.get()) {
            double dist = mc.player.distanceTo(target);
            mc.options.forwardKey.setPressed(walkInside.get() || dist > walkStopRange.get());
        }

        if (canAttackTarget(target)) {
            attack(target);
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target != null) {
            updateHumanizedAim(target, event.tickDelta);
            if (renderTarget.get()) event.renderer.box(target.getBoundingBox(), sideColor.get(), lineColor.get(), ShapeMode.Both, 0);
        }
    }

    private void updateHumanizedAim(Entity e, float tickDelta) {
        // FIXED: Using individual coordinates instead of getPos() for maximum compatibility
        double heightOffset = switch (aimPart.get()) {
            case Head -> e.getEyeHeight(e.getPose());
            case Feet -> 0.1;
            case Random -> random.nextDouble() * e.getEyeHeight(e.getPose());
            default -> e.getEyeHeight(e.getPose()) * 0.5; // Chest
        };

        // Create target vector manually to avoid getPos() error
        Vec3d targetPos = new Vec3d(e.getX(), e.getY() + heightOffset, e.getZ());
        
        double time = System.currentTimeMillis() * 0.003;
        double curveX = Math.sin(time) * curveIntensity.get();
        double curveY = Math.cos(time * 0.5) * curveIntensity.get();
        
        double jitterX = (random.nextDouble() - 0.5) * humanJitter.get();
        double jitterY = (random.nextDouble() - 0.5) * humanJitter.get();
        
        Vec3d aimVec = targetPos.add(curveX + jitterX, curveY + jitterY, curveX);
        
        float targetYaw = (float) Rotations.getYaw(aimVec);
        float targetPitch = (float) Rotations.getPitch(aimVec);

        float accel = (float) Math.min(1.0, smoothness.get() * (tickDelta + 0.5f));
        
        lastYaw += MathHelper.wrapDegrees(targetYaw - lastYaw) * accel;
        lastPitch += (targetPitch - lastPitch) * accel;

        if (lockCamera.get()) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
        }

        if (!packetLimit.get() || packetCounter < 1) {
            Rotations.rotate(lastYaw, lastPitch);
            packetCounter++;
        }
    }

    private boolean canAttackTarget(Entity e) {
        if (mc.player.distanceTo(e) > range.get()) return false;
        
        if (swingMode.get() == SwingMode.Dynamic) {
            if (mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return false;
        } else {
            int currentCps = (swingMode.get() == SwingMode.Randomized) ? 
                minCps.get() + random.nextInt(maxCps.get() - minCps.get() + 1) : minCps.get();
            if (System.currentTimeMillis() - lastAttackTime < (1000 / currentCps)) return false;
        }

        if (raycast.get()) {
            // Check against center of hitbox
            double angleDiff = Math.abs(MathHelper.wrapDegrees(lastYaw - Rotations.getYaw(e.getBoundingBox().getCenter())));
            if (angleDiff > maxAngle.get()) return false;
        }

        return true;
    }

    private void findTargets() {
        targets.clear();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (!entities.get().contains(e.getType())) continue;
            if (mc.player.distanceTo(e) > 8.0) continue;
            targets.add(e);
        }
        targets.sort((e1, e2) -> Double.compare(mc.player.distanceTo(e1), mc.player.distanceTo(e2)));
    }

    private void attack(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}