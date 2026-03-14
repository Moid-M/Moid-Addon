package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedList;

public class MoidTrails extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Colors");

    // --- Geometry ---
    private final Setting<Integer> trailLength = sg.add(new IntSetting.Builder().name("trail-length").defaultValue(40).min(1).max(1000).build());
    private final Setting<Double> ribbonHeight = sg.add(new DoubleSetting.Builder().name("height").defaultValue(0.2).min(0.01).sliderMax(2.0).build());
    private final Setting<Double> yOffset = sg.add(new DoubleSetting.Builder().name("y-offset").defaultValue(0.1).min(-1.0).max(1.0).build());

    // --- Color & Fade ---
    private final Setting<Boolean> chroma = sgColor.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Double> chromaSpeed = sgColor.add(new DoubleSetting.Builder().name("chroma-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(chroma::get).build());
    
    private final Setting<SettingColor> startColor = sgColor.add(new ColorSetting.Builder().name("start-color").defaultValue(new SettingColor(0, 255, 255, 200)).visible(() -> !chroma.get()).build());
    private final Setting<Boolean> useFadeColor = sgColor.add(new BoolSetting.Builder().name("use-fade-color").defaultValue(true).visible(() -> !chroma.get()).build());
    private final Setting<SettingColor> endColor = sgColor.add(new ColorSetting.Builder().name("end-color").defaultValue(new SettingColor(255, 0, 255, 50)).visible(() -> !chroma.get() && useFadeColor.get()).build());
    
    private final Setting<Double> fadeExponent = sgColor.add(new DoubleSetting.Builder().name("fade-curve").defaultValue(1.0).min(0.1).sliderMax(5.0).build());

    private final LinkedList<Vec3d> points = new LinkedList<>();
    private final Color workingColor = new Color();

    public MoidTrails() {
        super(AddonTemplate.CATEGORY, "moid-trails", "Customizable ribbon trails with dual-color fading and chroma.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        
        points.add(new Vec3d(mc.player.getX(), mc.player.getY() + yOffset.get(), mc.player.getZ()));
        
        while (points.size() > trailLength.get()) {
            points.removeFirst();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (points.size() < 2) return;

        double h = ribbonHeight.get();

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get(i + 1);

            // Progress goes from 0 (oldest/end of trail) to 1 (newest/at player)
            float progress = (float) i / points.size();
            
            updateWorkingColor(progress);
            
            if (workingColor.a <= 5) continue;

            // Render the vertical ribbon quad
            event.renderer.quad(
                p1.x, p1.y, p1.z,
                p1.x, p1.y + h, p1.z,
                p2.x, p2.y + h, p2.z,
                p2.x, p2.y, p2.z,
                workingColor
            );
        }
    }

    private void updateWorkingColor(float progress) {
        // Calculate dynamic alpha based on fade curve
        int alpha = (int) (startColor.get().a * Math.pow(progress, fadeExponent.get()));

        if (chroma.get()) {
            double hue = (System.currentTimeMillis() * (chromaSpeed.get() * 0.1) + (progress * 200)) % 360;
            java.awt.Color javaCol = java.awt.Color.getHSBColor((float) (hue / 360.0), 0.8f, 1.0f);
            workingColor.set(javaCol.getRed(), javaCol.getGreen(), javaCol.getBlue(), alpha);
            return;
        }

        if (useFadeColor.get()) {
            SettingColor s = startColor.get();
            SettingColor e = endColor.get();
            
            // Linear interpolation between start and end color
            workingColor.set(
                (int) (e.r + (s.r - e.r) * progress),
                (int) (e.g + (s.g - e.g) * progress),
                (int) (e.b + (s.b - e.b) * progress),
                alpha
            );
        } else {
            workingColor.set(startColor.get().r, startColor.get().g, startColor.get().b, alpha);
        }
    }
}