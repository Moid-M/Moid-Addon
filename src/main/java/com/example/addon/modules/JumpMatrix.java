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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class JumpMatrix extends Module {
    public enum RenderMode { Individual, Merged }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgChroma = settings.createGroup("Chroma");

    // --- Logic ---
    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(4.0).min(1).sliderMax(50.0).max(100.0).build());
    private final Setting<Double> scanSpeed = sg.add(new DoubleSetting.Builder().name("scan-speed").defaultValue(2.0).min(0.1).sliderMax(20.0).build());
    private final Setting<Boolean> onlyJump = sg.add(new BoolSetting.Builder().name("only-when-jump").defaultValue(true).build());
    private final Setting<Double> maxUp = sg.add(new DoubleSetting.Builder().name("max-height-up").defaultValue(2.0).min(0).sliderMax(10.0).build());
    private final Setting<Double> maxDown = sg.add(new DoubleSetting.Builder().name("max-height-down").defaultValue(3.0).min(0).sliderMax(10.0).build());

    // --- Render ---
    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>().name("render-mode").defaultValue(RenderMode.Individual).build());
    private final Setting<Double> width = sgRender.add(new DoubleSetting.Builder().name("width").defaultValue(0.02).min(0.01).sliderMax(0.2).build());
    private final Setting<SettingColor> colorStart = sgRender.add(new ColorSetting.Builder().name("color-start").defaultValue(new SettingColor(0, 255, 100, 255)).build());
    private final Setting<Boolean> useSecondColor = sgRender.add(new BoolSetting.Builder().name("use-second-color").defaultValue(false).build());
    private final Setting<SettingColor> colorEnd = sgRender.add(new ColorSetting.Builder().name("color-end").defaultValue(new SettingColor(0, 100, 255, 255)).visible(useSecondColor::get).build());
    private final Setting<Boolean> smoothFade = sgRender.add(new BoolSetting.Builder().name("smooth-fade").defaultValue(true).build());

    // --- Chroma ---
    private final Setting<Boolean> chroma = sgChroma.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Double> chromaSpeed = sgChroma.add(new DoubleSetting.Builder().name("chroma-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(chroma::get).build());

    private final List<MatrixPulse> pulses = new CopyOnWriteArrayList<>();
    private final BlockPos.Mutable mutablePos = new BlockPos.Mutable();
    private final Color workingColor = new Color();
    private boolean wasOnGround = true;

    public JumpMatrix() {
        super(AddonTemplate.CATEGORY, "jump-matrix", "Performance-optimized geometry matrix with merging capabilities.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (wasOnGround && !mc.player.isOnGround() && mc.player.getVelocity().y > 0 && (!onlyJump.get() || mc.options.jumpKey.isPressed())) {
            pulses.add(new MatrixPulse(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        }
        wasOnGround = mc.player.isOnGround();
        pulses.removeIf(p -> p.age > 1.0);
        for (MatrixPulse p : pulses) p.age += (scanSpeed.get() * 0.01);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (pulses.isEmpty()) return;
        for (MatrixPulse p : pulses) renderPulse(event, p);
    }

    private void renderPulse(Render3DEvent event, MatrixPulse p) {
        double currentRadius = p.age * maxRadius.get();
        double currentRadiusSq = currentRadius * currentRadius;
        double fadeWidth = smoothFade.get() ? currentRadius : 0.8;
        double innerRadiusSq = Math.pow(Math.max(0, currentRadius - fadeWidth), 2);

        int r = (int) Math.ceil(maxRadius.get());
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                double centerX = p.origin.x + x;
                double centerZ = p.origin.z + z;
                double dx = p.origin.x - (Math.floor(centerX) + 0.5);
                double dz = p.origin.z - (Math.floor(centerZ) + 0.5);
                double distSq = dx * dx + dz * dz;

                if (distSq < currentRadiusSq && distSq > innerRadiusSq) {
                    mutablePos.set(centerX, p.origin.y, centerZ);
                    BlockPos surfacePos = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, mutablePos).down();
                    
                    double yDiff = surfacePos.getY() - p.origin.y;
                    if (yDiff > maxUp.get() || yDiff < -maxDown.get()) continue;

                    BlockState state = mc.world.getBlockState(surfacePos);
                    if (state.isAir()) continue;

                    updateWorkingColor((float) (Math.sqrt(distSq) / maxRadius.get()), (int) (colorStart.get().a * (1.0 - p.age) * (smoothFade.get() ? (1.0 - ((currentRadius - Math.sqrt(distSq)) / currentRadius)) : 1.0)));
                    drawGeometryEdges(event.renderer, surfacePos, state, workingColor);
                }
            }
        }
    }

    private void updateWorkingColor(float progress, int alpha) {
        if (alpha <= 10) { workingColor.a = 0; return; }
        if (chroma.get()) {
            double hue = (System.currentTimeMillis() * (chromaSpeed.get() * 0.1) + (progress * 100)) % 360;
            java.awt.Color javaCol = java.awt.Color.getHSBColor((float) (hue / 360.0), 0.8f, 1.0f);
            workingColor.set(javaCol.getRed(), javaCol.getGreen(), javaCol.getBlue(), alpha);
        } else {
            SettingColor cS = colorStart.get();
            SettingColor cE = useSecondColor.get() ? colorEnd.get() : cS;
            workingColor.set((int) (cS.r + (cE.r - cS.r) * progress), (int) (cS.g + (cE.g - cS.g) * progress), (int) (cS.b + (cE.b - cS.b) * progress), alpha);
        }
    }

    private void drawGeometryEdges(Renderer3D renderer, BlockPos p, BlockState state, Color col) {
        if (col.a <= 0) return;
        VoxelShape shape = state.getOutlineShape(mc.world, p);
        if (shape.isEmpty()) return;

        List<Box> boxes = shape.getBoundingBoxes();
        double w = width.get();
        boolean merged = renderMode.get() == RenderMode.Merged;
        
        for (Box box : boxes) {
            double x1 = p.getX() + box.minX;
            double y1 = p.getY() + box.maxY + 0.011;
            double z1 = p.getZ() + box.minZ;
            double x2 = p.getX() + box.maxX;
            double z2 = p.getZ() + box.maxZ;

            // Merging Logic: Only draw the quad if the neighbor is NOT at the same height/state
            // North
            if (!merged || isDifferent(p.north(), y1))
                renderer.quad(x1 - w, y1, z1 - w, x1 - w, y1, z1 + w, x2 + w, y1, z1 + w, x2 + w, y1, z1 - w, col);
            // South
            if (!merged || isDifferent(p.south(), y1))
                renderer.quad(x1 - w, y1, z2 - w, x1 - w, y1, z2 + w, x2 + w, y1, z2 + w, x2 + w, y1, z2 - w, col);
            // West
            if (!merged || isDifferent(p.west(), y1))
                renderer.quad(x1 - w, y1, z1 - w, x1 + w, y1, z1 - w, x1 + w, y1, z2 + w, x1 - w, y1, z2 + w, col);
            // East
            if (!merged || isDifferent(p.east(), y1))
                renderer.quad(x2 - w, y1, z1 - w, x2 + w, y1, z1 - w, x2 + w, y1, z2 + w, x2 - w, y1, z2 + w, col);
        }
    }

    private boolean isDifferent(BlockPos neighbor, double currentY) {
        // We check the top position of the neighbor to see if it's at the same level
        BlockPos top = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, neighbor).down();
        VoxelShape nShape = mc.world.getBlockState(top).getOutlineShape(mc.world, top);
        if (nShape.isEmpty()) return true;
        
        double neighborY = top.getY() + nShape.getMax(net.minecraft.util.math.Direction.Axis.Y) + 0.011;
        // If the heights match, they are NOT different (so we don't draw the edge)
        return Math.abs(neighborY - currentY) > 0.02;
    }

    private static class MatrixPulse {
        public final Vec3d origin;
        public double age = 0;
        public MatrixPulse(Vec3d origin) { this.origin = origin; }
    }
}