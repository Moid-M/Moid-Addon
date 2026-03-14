package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;

import java.util.Set;

public class TargetHUD extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");

    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(15.0).min(1).sliderMax(64).build());

    // --- Visuals ---
    private final Setting<Double> xPct = sgVisuals.add(new DoubleSetting.Builder().name("X-Percent").defaultValue(50).min(0).max(100).build());
    private final Setting<Double> yPct = sgVisuals.add(new DoubleSetting.Builder().name("Y-Percent").defaultValue(50).min(0).max(100).build());
    private final Setting<Double> scale = sgVisuals.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(3.0).build());
    private final Setting<Double> nameScale = sgVisuals.add(new DoubleSetting.Builder().name("name-scale").defaultValue(1.2).min(0.5).max(2.0).build());
    
    // --- Background Effects ---
    private final Setting<Boolean> useGradient = sgVisuals.add(new BoolSetting.Builder().name("gradient-bg").defaultValue(true).build());
    private final Setting<SettingColor> bgColor1 = sgVisuals.add(new ColorSetting.Builder().name("bg-color-left").defaultValue(new SettingColor(20, 20, 20, 180)).build());
    private final Setting<SettingColor> bgColor2 = sgVisuals.add(new ColorSetting.Builder().name("bg-color-right").defaultValue(new SettingColor(40, 40, 40, 180)).visible(useGradient::get).build());

    private LivingEntity target;
    private double animHealth = 0;
    private final Color renderColor = new Color();

    public TargetHUD() {
        super(AddonTemplate.CATEGORY, "TargetHUD", "High-fidelity smooth TargetHUD.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.world == null || mc.player == null) return;
        findTarget();
        if (target == null) {
            animHealth = 0;
            return;
        }
        animHealth = MathHelper.lerp(0.12f * (float)event.tickDelta, (float)animHealth, target.getHealth());
        renderHUD(event.drawContext);
    }

    private void findTarget() {
        double closestDist = range.get();
        target = null;
        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof LivingEntity living) || entity == mc.player || !entity.isAlive() || !entities.get().contains(entity.getType())) continue;
            double dist = mc.player.distanceTo(entity);
            if (dist <= closestDist) {
                closestDist = dist;
                target = living;
            }
        }
    }

    private void renderHUD(DrawContext context) {
        float s = scale.get().floatValue();
        float x = (float) (mc.getWindow().getScaledWidth() * (xPct.get() / 100.0));
        float y = (float) (mc.getWindow().getScaledHeight() * (yPct.get() / 100.0));

        int w = 160;
        int h = 65;

        context.getMatrices().pushMatrix();
        context.getMatrices().translate(x, y); 
        context.getMatrices().scale(s, s);

        // 1. IMPROVED GRADIENT BACKGROUND
        // fillGradient is a native Minecraft method - very stable and smooth
        int c1 = bgColor1.get().getPacked();
        int c2 = useGradient.get() ? bgColor2.get().getPacked() : c1;
        context.fillGradient(0, 0, w, h, c1, c2);

        // 2. NAME (Bigger & Scaled)
        context.getMatrices().pushMatrix();
        float ns = nameScale.get().floatValue();
        context.getMatrices().scale(ns, ns);
        context.drawTextWithShadow(mc.textRenderer, target.getName().getString(), (int)(8/ns), (int)(6/ns), -1);
        context.getMatrices().popMatrix();

        // 3. HEALTH BAR
        float actualHpRatio = MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0, 1);
        float animHpRatio = (float) MathHelper.clamp(animHealth / target.getMaxHealth(), 0, 1);
        
        // Bar Housing
        context.fill(8, 22, w - 8, 30, 0x88000000);
        
        // Color transition
        int r = (int) (255 * (1 - actualHpRatio));
        int g = (int) (255 * actualHpRatio);
        renderColor.set(r, g, 50, 255);
        
        int barWidth = (int) ((w - 16) * animHpRatio);
        context.fill(8, 22, 8 + barWidth, 30, renderColor.getPacked());

        // 4. GEAR
        int itemX = 8;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = target.getEquippedStack(slot);
            if (!stack.isEmpty()) {
                context.drawItem(stack, itemX, 35);
                context.drawStackOverlay(mc.textRenderer, stack, itemX, 35);
            }
            itemX += 18;
        }
        context.drawItem(target.getMainHandStack(), w - 26, 35);

        context.getMatrices().popMatrix();
    }
}