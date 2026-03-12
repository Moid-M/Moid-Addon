package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;
import java.util.Set;

public class Triggerbot extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final Random random = new Random();

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .description("Entities to attack.")
        .defaultValue(Set.of(EntityType.PLAYER))
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .description("Maximum distance to attack.")
        .defaultValue(3.2)
        .min(1)
        .sliderMax(6)
        .build()
    );

    private final Setting<Boolean> onlyCrits = sgGeneral.add(new BoolSetting.Builder()
        .name("only-crits")
        .description("Only attack when you will land a critical hit.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> attackDelay = sgGeneral.add(new IntSetting.Builder()
        .name("attack-delay")
        .description("Base ticks between consecutive hits.")
        .defaultValue(2)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> randomDelay = sgGeneral.add(new IntSetting.Builder()
        .name("random-delay")
        .description("Max random ticks to add to the base delay.")
        .defaultValue(2)
        .min(0)
        .sliderMax(10)
        .build()
    );

    private int attackTimer;

    public Triggerbot() {
        super(AddonTemplate.CATEGORY, "triggerbot", "Advanced Triggerbot - Simplified Detection.");
    }

    @Override
    public void onActivate() {
        attackTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.interactionManager == null || mc.world == null) return;

        if (attackTimer > 0) {
            attackTimer--;
            return;
        }

        // Simpler, more reliable hit detection
        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            Entity target = ((EntityHitResult) mc.crosshairTarget).getEntity();

            // 1. Basic Validation (Range & Entity Type)
            if (target == null || !target.isAlive() || mc.player.distanceTo(target) > range.get()) return;
            if (!entities.get().contains(target.getType())) return;

            // 2. Critical Logic
            if (onlyCrits.get()) {
                // fallDistance > 0 means you are in the air and moving down
                boolean isFalling = mc.player.fallDistance > 0 
                    && !mc.player.isOnGround() 
                    && !mc.player.isClimbing() 
                    && !mc.player.isTouchingWater();
                
                if (!isFalling) return;
            }

            // 3. Attack
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            
            // 4. Timer with Randomization
            int r = randomDelay.get() > 0 ? random.nextInt(randomDelay.get() + 1) : 0;
            attackTimer = attackDelay.get() + r;
        }
    }
}