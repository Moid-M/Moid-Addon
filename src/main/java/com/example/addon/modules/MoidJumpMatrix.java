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
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MoidJumpMatrix extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChroma = settings.createGroup("Chroma");

    // --- Logic ---
    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(4.0).min(1).sliderMax(10.0).build());
    private final Setting<Double> scanSpeed = sg.add(new DoubleSetting.Builder().name("scan-speed").defaultValue(2.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> onlyJump = sg.add(new BoolSetting.Builder().name("only-when-jump").defaultValue(true).build());

    // --- Static Render ---
    private final Setting<Integer> thickness = sgRender.add(new IntSetting.Builder().name("thickness").defaultValue(1).min(1).max(5).build());
    private final Setting<SettingColor> colorStart = sgRender.add(new ColorSetting.Builder().name("color-start").defaultValue(new SettingColor(0, 255, 100, 255)).build());
    private final Setting<Boolean> useSecondColor = sgRender.add(new BoolSetting.Builder().name("use-second-color").defaultValue(false).build());
    private final Setting<SettingColor> colorEnd = sgRender.add(new ColorSetting.Builder().name("color-end").defaultValue(new SettingColor(0, 100, 255, 255)).visible(useSecondColor::get).build());
    private final Setting<Boolean> smoothFade = sgRender.add(new BoolSetting.Builder().name("smooth-fade").defaultValue(true).build());

    // --- Chroma Settings ---
    private final Setting<Boolean> chroma = sgChroma.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Double> chromaSpeed = sgChroma.add(new DoubleSetting.Builder().name("chroma-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(chroma::get).build());

    private final List<MatrixPulse> pulses = Collections.synchronizedList(new ArrayList<>());
    private boolean wasOnGround = true;

    public MoidJumpMatrix() {
        super(AddonTemplate.CATEGORY, "moid-jump-matrix", "Chroma-powered matrix jump effect.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        
        boolean isJumping = !onlyJump.get() || mc.options.jumpKey.isPressed();
        
        if (wasOnGround && !mc.player.isOnGround() && mc.player.getVelocity().y > 0 && isJumping) {
            pulses.add(new MatrixPulse(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        }
        
        wasOnGround = mc.player.isOnGround();
        
        synchronized (pulses) {
            pulses.removeIf(p -> p.age > 1.0);
            for (MatrixPulse p : pulses) {
                p.age += (scanSpeed.get() * 0.01);
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (pulses.isEmpty()) return;

        synchronized (pulses) {
            for (MatrixPulse p : pulses) {
                renderPulse(event, p);
            }
        }
    }

    private void renderPulse(Render3DEvent event, MatrixPulse p) {
        double currentRadius = p.age * maxRadius.get();
        int r = (int) Math.ceil(maxRadius.get());

        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double posX = p.origin.x + x;
                double posZ = p.origin.z + z;
                
                double dist = Math.sqrt(Math.pow(p.origin.x - (Math.floor(posX) + 0.5), 2) + 
                                       Math.pow(p.origin.z - (Math.floor(posZ) + 0.5), 2));

                double fadeWidth = smoothFade.get() ? currentRadius : 0.8;

                if (dist < currentRadius && dist > currentRadius - fadeWidth) {
                    BlockPos bp = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 
                            BlockPos.ofFloored(posX, p.origin.y, posZ)).down();
                    
                    BlockState state = mc.world.getBlockState(bp);
                    if (state.isAir()) continue;

                    float progress = (float) (dist / maxRadius.get());
                    double distanceFactor = smoothFade.get() ? (1.0 - ((currentRadius - dist) / currentRadius)) : 1.0;
                    
                    int alpha = (int) (colorStart.get().a * (1.0 - p.age) * distanceFactor);
                    if (alpha <= 10) continue;

                    Color drawCol = getCol(progress, alpha, p.age);
                    drawBlockEdges(event.renderer, bp, drawCol);
                }
            }
        }
    }

    private Color getCol(float progress, int alpha, double age) {
        if (chroma.get()) {
            // Calculate Hue based on time and pulse age
            double hue = (System.currentTimeMillis() * (chromaSpeed.get() * 0.1) + (progress * 100)) % 360;
            java.awt.Color javaCol = java.awt.Color.getHSBColor((float) (hue / 360.0), 0.8f, 1.0f);
            return new Color(javaCol.getRed(), javaCol.getGreen(), javaCol.getBlue(), alpha);
        }

        SettingColor cS = colorStart.get();
        SettingColor cE = useSecondColor.get() ? colorEnd.get() : cS;
        
        return new Color(
            (int) (cS.r + (cE.r - cS.r) * progress),
            (int) (cS.g + (cE.g - cS.g) * progress),
            (int) (cS.b + (cE.b - cS.b) * progress),
            alpha
        );
    }

    private void drawBlockEdges(Renderer3D renderer, BlockPos p, Color col) {
        double x1 = p.getX(), y1 = p.getY() + 1.01, z1 = p.getZ();
        double x2 = x1 + 1, z2 = z1 + 1;

        for (int i = 0; i < thickness.get(); i++) {
            double offset = i * 0.005;
            renderer.line(x1 - offset, y1, z1 - offset, x2 + offset, y1, z1 - offset, col);
            renderer.line(x1 - offset, y1, z1 - offset, x1 - offset, y1, z2 + offset, col);
            renderer.line(x2 + offset, y1, z1 - offset, x2 + offset, y1, z2 + offset, col);
            renderer.line(x1 - offset, y1, z2 + offset, x2 + offset, y1, z2 + offset, col);
        }
    }

    private static class MatrixPulse {
        public final Vec3d origin;
        public double age = 0;
        public MatrixPulse(Vec3d origin) { this.origin = origin; }
    }
}