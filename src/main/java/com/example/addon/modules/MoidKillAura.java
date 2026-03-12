package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class MoidKillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRotations = settings.createGroup("Rotations & Smoothing");
    private final SettingGroup sgSwing = settings.createGroup("Swing & CPS");
    private final SettingGroup sgMovement = settings.createGroup("Movement & Auto-Pilot");
    private final SettingGroup sgBypass = settings.createGroup("Bypass & Strategy");
    private final SettingGroup sgSwitch = settings.createGroup("Multi-Target (Switch)");

    // --- General ---
    public enum SortMode { Distance, Health, Armor }
    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>().name("sort-mode").defaultValue(SortMode.Distance).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("attack-range").defaultValue(3.1).min(1).sliderMax(6).build());
    private final Setting<Double> scanRange = sgGeneral.add(new DoubleSetting.Builder().name("scan-range").defaultValue(5.0).min(1).sliderMax(15).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());

    // --- Precision Rotations ---
    public enum RotationMode { Tick, Frame }
    public enum AimTarget { Head, Chest, Feet }
    private final Setting<RotationMode> rotMode = sgRotations.add(new EnumSetting.Builder<RotationMode>().name("rotation-mode").defaultValue(RotationMode.Frame).build());
    private final Setting<AimTarget> aimTarget = sgRotations.add(new EnumSetting.Builder<AimTarget>().name("aim-bone").defaultValue(AimTarget.Chest).build());
    private final Setting<Double> rotSpeed = sgRotations.add(new DoubleSetting.Builder().name("aim-intensity").defaultValue(0.15).min(0.01).sliderMax(1.0).build());
    private final Setting<Boolean> lockCamera = sgRotations.add(new BoolSetting.Builder().name("lock-camera").defaultValue(false).build());
    private final Setting<Double> minJitter = sgRotations.add(new DoubleSetting.Builder().name("min-jitter").defaultValue(0.05).min(0).sliderMax(0.5).build());
    private final Setting<Double> maxJitter = sgRotations.add(new DoubleSetting.Builder().name("max-jitter").defaultValue(0.15).min(0).sliderMax(1.0).build());
    private final Setting<Boolean> smoothJitter = sgRotations.add(new BoolSetting.Builder().name("smooth-jitter").defaultValue(true).build());
    private final Setting<Boolean> prediction = sgRotations.add(new BoolSetting.Builder().name("predict-motion").defaultValue(true).build());
    private final Setting<Double> predictAmount = sgRotations.add(new DoubleSetting.Builder().name("predict-scale").defaultValue(1.2).min(0).visible(prediction::get).build());

    // --- Swing & CPS Settings ---
    public enum SwingMode { Dynamic, Static, Randomized }
    private final Setting<SwingMode> swingMode = sgSwing.add(new EnumSetting.Builder<SwingMode>().name("swing-mode").defaultValue(SwingMode.Dynamic).build());
    private final Setting<Integer> minCps = sgSwing.add(new IntSetting.Builder().name("min-cps").defaultValue(8).min(1).max(20).visible(() -> swingMode.get() != SwingMode.Dynamic).build());
    private final Setting<Integer> maxCps = sgSwing.add(new IntSetting.Builder().name("max-cps").defaultValue(12).min(1).max(20).visible(() -> swingMode.get() == SwingMode.Randomized).build());

    // --- Remaining Categories (Kept from V7.2) ---
    private final Setting<Boolean> walkCorrection = sgMovement.add(new BoolSetting.Builder().name("walk-correction").defaultValue(true).build());
    private final Setting<Boolean> autoWalk = sgMovement.add(new BoolSetting.Builder().name("auto-walk").defaultValue(false).build());
    private final Setting<Boolean> autoSprint = sgMovement.add(new BoolSetting.Builder().name("auto-sprint").defaultValue(false).visible(autoWalk::get).build());
    private final Setting<Boolean> critMode = sgBypass.add(new BoolSetting.Builder().name("only-crits").defaultValue(false).build());
    private final Setting<Integer> hitChance = sgBypass.add(new IntSetting.Builder().name("hit-chance").defaultValue(100).min(0).max(100).build());
    private final Setting<Boolean> shieldBreaker = sgBypass.add(new BoolSetting.Builder().name("shield-breaker").defaultValue(true).build());
    private final Setting<Boolean> wTap = sgBypass.add(new BoolSetting.Builder().name("w-tap").defaultValue(true).build());
    private final Setting<Boolean> switchMode = sgSwitch.add(new BoolSetting.Builder().name("switch-aura").defaultValue(false).build());
    private final Setting<Integer> switchDelay = sgSwitch.add(new IntSetting.Builder().name("switch-ticks").defaultValue(10).min(1).visible(switchMode::get).build());

    private Entity target;
    private final List<Entity> targets = new ArrayList<>();
    private final Random random = new Random();
    private float lastYaw, lastPitch;
    private int switchTimer, targetIndex;
    private long lastAttackTime;
    private Vec3d currentJitterOffset = Vec3d.ZERO;

    public MoidKillAura() {
        super(AddonTemplate.CATEGORY, "moid-kill-aura", "Moid V8.0: Ease-Out Aim & Advanced Swing.");
    }

    @Override
    public void onActivate() {
        lastYaw = mc.player.getYaw();
        lastPitch = mc.player.getPitch();
        target = null;
        switchTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        updateTargets();
        if (targets.isEmpty()) {
            target = null;
            if (autoWalk.get()) mc.options.forwardKey.setPressed(false);
            return;
        }

        // Switch Logic
        if (switchMode.get()) {
            if (switchTimer >= switchDelay.get()) {
                targetIndex = (targetIndex + 1) % targets.size();
                switchTimer = 0;
            }
            target = targets.get(Math.min(targetIndex, targets.size() - 1));
            switchTimer++;
        } else target = targets.get(0);

        if (rotMode.get() == RotationMode.Tick) updateRotations(target, 1.0f);
        handleMovement(target);

        // Attack Execution
        if (mc.player.distanceTo(target) <= range.get() && canAttack()) {
            if (random.nextInt(100) < hitChance.get()) {
                if (shieldBreaker.get() && target instanceof PlayerEntity p && p.isBlocking()) doShieldBreak();
                if (wTap.get()) mc.player.setSprinting(false);
                attack(target);
                if (wTap.get()) mc.player.setSprinting(true);
                lastAttackTime = System.currentTimeMillis();
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (target != null && rotMode.get() == RotationMode.Frame) {
            updateRotations(target, event.tickDelta);
        }
    }

    private void updateRotations(Entity e, float delta) {
        Vec3d aimPos = getTargetPos(e, delta);
        float targetYaw = (float) Rotations.getYaw(aimPos);
        float targetPitch = (float) Rotations.getPitch(aimPos);

        // EASE-OUT SMOOTHING (True 165Hz feel)
        // Instead of a constant speed, we move a % of the distance remaining.
        float yawDiff = MathHelper.wrapDegrees(targetYaw - lastYaw);
        float pitchDiff = targetPitch - lastPitch;

        float intensity = rotSpeed.get().floatValue();
        lastYaw += yawDiff * intensity;
        lastPitch += pitchDiff * intensity;

        if (lockCamera.get()) {
            mc.player.setYaw(lastYaw);
            mc.player.setPitch(lastPitch);
        } else Rotations.rotate(lastYaw, lastPitch);
    }

    private Vec3d getTargetPos(Entity e, float delta) {
        double yOffset = switch (aimTarget.get()) {
            case Head -> e.getEyeHeight(e.getPose());
            case Chest -> e.getEyeHeight(e.getPose()) * 0.5;
            case Feet -> 0.1;
        };
        Vec3d basePos = new Vec3d(e.getX(), e.getY() + yOffset, e.getZ());

        // Jitter with Delta-Time Lerp
        double jitterVal = minJitter.get() + (random.nextDouble() * (maxJitter.get() - minJitter.get()));
        Vec3d newJitter = new Vec3d((random.nextDouble()-0.5)*jitterVal, (random.nextDouble()-0.5)*jitterVal, (random.nextDouble()-0.5)*jitterVal);
        currentJitterOffset = smoothJitter.get() ? currentJitterOffset.lerp(newJitter, delta * 0.5) : newJitter;
        basePos = basePos.add(currentJitterOffset);

        if (prediction.get()) {
            Vec3d vel = e.getVelocity();
            basePos = basePos.add(vel.x * predictAmount.get(), vel.y * predictAmount.get(), vel.z * predictAmount.get());
        }
        return basePos;
    }

    private boolean canAttack() {
        if (critMode.get() && !isCritReady()) return false;

        return switch (swingMode.get()) {
            case Dynamic -> mc.player.getAttackCooldownProgress(0.5f) >= 1.0f;
            case Static -> System.currentTimeMillis() - lastAttackTime >= (1000 / minCps.get());
            case Randomized -> {
                int randomCps = minCps.get() + random.nextInt(maxCps.get() - minCps.get() + 1);
                yield System.currentTimeMillis() - lastAttackTime >= (1000 / randomCps);
            }
        };
    }

    private boolean isCritReady() {
        return mc.player.fallDistance > 0 && !mc.player.isOnGround() && !mc.player.isClimbing() && !mc.player.isTouchingWater();
    }

    private void handleMovement(Entity e) {
        float targetYaw = (float) Rotations.getYaw(new Vec3d(e.getX(), e.getY() + e.getEyeHeight(e.getPose()), e.getZ()));
        if (autoWalk.get()) {
            mc.options.forwardKey.setPressed(true);
            if (autoSprint.get()) mc.player.setSprinting(true);
            mc.player.setYaw(targetYaw);
        } else if (walkCorrection.get() && mc.options.forwardKey.isPressed()) {
            Rotations.rotate(targetYaw, mc.player.getPitch());
        }
    }

    private void updateTargets() {
        targets.clear();
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !e.isAlive() || e.isRemoved() || !entities.get().contains(e.getType())) continue;
            if (mc.player.distanceTo(e) > scanRange.get()) continue;
            targets.add(e);
        }
        targets.sort((e1, e2) -> switch (sortMode.get()) {
            case Distance -> Double.compare(mc.player.distanceTo(e1), mc.player.distanceTo(e2));
            case Health -> Float.compare(e1 instanceof LivingEntity le ? le.getHealth() : 0, e2 instanceof LivingEntity le ? le.getHealth() : 0);
            case Armor -> Integer.compare(e1 instanceof LivingEntity le ? le.getArmor() : 0, e2 instanceof LivingEntity le ? le.getArmor() : 0);
        });
    }

    private void doShieldBreak() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
                InvUtils.swap(i, false);
                return;
            }
        }
    }

    private void attack(Entity entity) {
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}