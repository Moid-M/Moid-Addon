package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.lwjgl.opengl.GL11;

import java.util.Set;

public class MoidCircleESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAnimation = settings.createGroup("Animations");
    private final SettingGroup sgFade = settings.createGroup("Fade & Glow");

    // --- General ---
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("radius").defaultValue(3.0).min(0).sliderMax(6).build());
    private final Setting<Integer> thickness = sgGeneral.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(10).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<SettingColor> baseColor = sgGeneral.add(new ColorSetting.Builder().name("base-color").defaultValue(new SettingColor(0, 255, 255, 255)).build());
    private final Setting<SettingColor> targetColor = sgGeneral.add(new ColorSetting.Builder().name("target-color").defaultValue(new SettingColor(255, 0, 0, 255)).build());
    private final Setting<Boolean> depthTest = sgGeneral.add(new BoolSetting.Builder().name("depth-test").defaultValue(true).build());

    // --- Animation ---
    private final Setting<Boolean> spinning = sgAnimation.add(new BoolSetting.Builder().name("spinning").defaultValue(true).build());
    private final Setting<Double> spinSpeed = sgAnimation.add(new DoubleSetting.Builder().name("spin-speed").defaultValue(2.0).min(0.0).sliderMax(10.0).visible(spinning::get).build());
    private final Setting<Boolean> breathing = sgAnimation.add(new BoolSetting.Builder().name("breathing").defaultValue(true).build());
    private final Setting<Double> breatheSpeed = sgAnimation.add(new DoubleSetting.Builder().name("breathe-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(breathing::get).build());
    private final Setting<Double> breatheIntensity = sgAnimation.add(new DoubleSetting.Builder().name("breathe-intensity").defaultValue(0.15).min(0.0).sliderMax(1.0).visible(breathing::get).build());
    private final Setting<Integer> segments = sgAnimation.add(new IntSetting.Builder().name("segments").defaultValue(120).min(3).max(1000).build());

    // --- Fade ---
    private final Setting<Boolean> useFade = sgFade.add(new BoolSetting.Builder().name("enable-fade").defaultValue(true).build());
    private final Setting<Integer> fadeLayers = sgFade.add(new IntSetting.Builder().name("fade-layers").defaultValue(8).min(1).max(20).visible(useFade::get).build());
    private final Setting<Double> fadeDensity = sgFade.add(new DoubleSetting.Builder().name("fade-density").defaultValue(0.02).min(0.001).sliderMax(0.1).visible(useFade::get).build());

    private final Color drawColor = new Color();

    public MoidCircleESP() {
        super(AddonTemplate.CATEGORY, "moid-circle-esp", "V5.0: The definitive 1000-segment glow circle.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        // Depth Logic
        if (depthTest.get()) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }

        boolean targetInRange = false;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;
            if (mc.player.distanceTo(entity) <= range.get()) {
                targetInRange = true;
                break;
            }
        }

        double x = mc.player.lastRenderX + (mc.player.getX() - mc.player.lastRenderX) * event.tickDelta;
        double y = (mc.player.lastRenderY + (mc.player.getY() - mc.player.lastRenderY) * event.tickDelta) + 0.02;
        double z = mc.player.lastRenderZ + (mc.player.getZ() - mc.player.lastRenderZ) * event.tickDelta;

        double radius = range.get();
        if (breathing.get()) {
            radius += Math.sin(System.currentTimeMillis() * 0.001 * breatheSpeed.get()) * breatheIntensity.get();
        }

        double rotation = spinning.get() ? (System.currentTimeMillis() * 0.1 * spinSpeed.get()) : 0;
        SettingColor coreCol = targetInRange ? targetColor.get() : baseColor.get();

        int segs = segments.get();
        double step = 360.0 / segs;

        for (int i = 0; i < segs; i++) {
            double ang1 = Math.toRadians((i * step) + rotation);
            double ang2 = Math.toRadians(((i + 1) * step) + rotation);

            double cos1 = Math.cos(ang1); double sin1 = Math.sin(ang1);
            double cos2 = Math.cos(ang2); double sin2 = Math.sin(ang2);

            // 1. Draw Core Thickness
            drawColor.set(coreCol);
            for (int t = 0; t < thickness.get(); t++) {
                double hOffset = t * 0.002;
                event.renderer.line(x + cos1 * radius, y + hOffset, z + sin1 * radius, x + cos2 * radius, y + hOffset, z + sin2 * radius, drawColor);
            }

            // 2. Draw Smooth Fade Glow
            if (useFade.get()) {
                for (int f = 1; f <= fadeLayers.get(); f++) {
                    double fRadius = radius + (f * fadeDensity.get());
                    // Exponential alpha drop-off for a "soft" look
                    int alpha = (int) (coreCol.a * Math.pow(0.7, f)); 
                    if (alpha < 1) break;
                    
                    drawColor.set(coreCol.r, coreCol.g, coreCol.b, alpha);
                    event.renderer.line(x + cos1 * fRadius, y, z + sin1 * fRadius, x + cos2 * fRadius, y, z + sin2 * fRadius, drawColor);
                }
            }
        }
        
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }
}