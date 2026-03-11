package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Random;
import java.util.Set;
import java.util.stream.StreamSupport;

public class MoidKillAura extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Bypass & Simulation");
    private final SettingGroup sgMovement = settings.createGroup("Movement Settings");

    public enum SortMode { Distance, Health, Armor }
    private final Setting<SortMode> sortMode = sgGeneral.add(new EnumSetting.Builder<SortMode>().name("sort-mode").defaultValue(SortMode.Distance).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(2.9).min(1).sliderMax(6).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());

    // Bypass & Combat Tech
    private final Setting<Boolean> critMode = sgBypass.add(new BoolSetting.Builder().name("critical-hits").description("Automatically jumps to guarantee critical damage.").defaultValue(false).build());
    private final Setting<Boolean> raycast = sgBypass.add(new BoolSetting.Builder().name("raycast").description("Only hits if looking at target hitbox.").defaultValue(true).build());
    private final Setting<Boolean> simulationFix = sgBypass.add(new BoolSetting.Builder().name("gl-simulation-fix").defaultValue(true).build());
    private final Setting<Integer> rotationBuffer = sgBypass.add(new IntSetting.Builder().name("rotation-buffer").defaultValue(1).min(0).max(3).build());
    private final Setting<Integer> hitChance = sgBypass.add(new IntSetting.Builder().name("hit-chance").defaultValue(100).min(0).max(100).build());

    private final Setting<Boolean> wTap = sgMovement.add(new BoolSetting.Builder().name("w-tap").description("Resets sprint for extra knockback.").defaultValue(true).build());
    private final Setting<Boolean> autoWalk = sgMovement.add(new BoolSetting.Builder().name("auto-walk").defaultValue(false).build());
    private final Setting<Boolean> moveCorrection = sgMovement.add(new BoolSetting.Builder().name("move-correction").defaultValue(false).build());

    private Entity target;
    private final Random random = new Random();
    private int ticksSinceRotation = 0;
    private float lastYaw, lastPitch;

    public MoidKillAura() {
        super(AddonTemplate.CATEGORY, "moid-kill-aura", "Moid-Aura V3.2: Anti-Kick Validation & Optional Crits.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        target = findTarget();
        if (target == null) {
            ticksSinceRotation = 0;
            return;
        }

        Vec3d aimPos = target.getBoundingBox().getCenter();
        applySmoothRotations(Rotations.getYaw(aimPos), Rotations.getPitch(aimPos));
        ticksSinceRotation++;

        handleMovement(target);

        if (canAttack() && ticksSinceRotation >= rotationBuffer.get()) {
            if (raycast.get() && !mc.player.canSee(target)) return;
            
            if (random.nextInt(100) < hitChance.get()) {
                // Critical Hit Logic: Jump if on ground and crits are enabled
                if (critMode.get() && mc.player.isOnGround()) {
                    mc.player.jump();
                }

                if (wTap.get() || simulationFix.get()) mc.player.setSprinting(false);
                attack(target);
            } else {
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    private void handleMovement(Entity entity) {
        if (autoWalk.get()) mc.options.forwardKey.setPressed(true);
        if (moveCorrection.get() || autoWalk.get()) {
            mc.player.setYaw((float) Rotations.getYaw(entity.getBoundingBox().getCenter()));
        }
    }

    private void applySmoothRotations(double yaw, double pitch) {
        lastYaw = MathHelper.stepTowards(lastYaw, (float) yaw, 38f);
        lastPitch = MathHelper.stepTowards(lastPitch, (float) pitch, 38f);
        Rotations.rotate(lastYaw, lastPitch);
    }

    private Entity findTarget() {
        var targets = StreamSupport.stream(mc.world.getEntities().spliterator(), false)
            .filter(e -> e != mc.player && e.isAlive() && !e.isRemoved() && entities.get().contains(e.getType()))
            .filter(e -> mc.player.getEyePos().distanceTo(e.getBoundingBox().getCenter()) <= range.get());

        return switch (sortMode.get()) {
            case Distance -> targets.min(Comparator.comparingDouble(e -> mc.player.distanceTo(e))).orElse(null);
            case Health -> targets.min(Comparator.comparingDouble(e -> e instanceof LivingEntity ? ((LivingEntity) e).getHealth() : 999)).orElse(null);
            case Armor -> targets.min(Comparator.comparingDouble(e -> e instanceof LivingEntity ? ((LivingEntity) e).getArmor() : 999)).orElse(null);
        };
    }

    private boolean canAttack() {
        // If CritMode is on, wait until we are falling to hit for maximum damage
        if (critMode.get() && mc.player.getVelocity().y >= 0 && !mc.player.isOnGround()) return false;
        
        return mc.player.getAttackCooldownProgress(0.5f) >= 0.95f;
    }

    private void attack(Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) {
            target = null;
            return;
        }
        
        mc.interactionManager.attackEntity(mc.player, entity);
        mc.player.swingHand(Hand.MAIN_HAND);
    }
}
