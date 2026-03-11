package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import java.util.*;

public class MoidJumpMatrix extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(4.0).min(1).sliderMax(10.0).build());
    private final Setting<Double> scanSpeed = sg.add(new DoubleSetting.Builder().name("scan-speed").defaultValue(2.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Boolean> onlyJump = sg.add(new BoolSetting.Builder().name("only-when-jump").defaultValue(true).build());

    private final Setting<Integer> thickness = sgRender.add(new IntSetting.Builder().name("thickness").defaultValue(1).min(1).max(10).build());
    private final Setting<SettingColor> colorStart = sgRender.add(new ColorSetting.Builder().name("color-start").defaultValue(new SettingColor(0, 255, 100, 255)).build());
    private final Setting<Boolean> useSecondColor = sgRender.add(new BoolSetting.Builder().name("use-second-color").defaultValue(false).build());
    private final Setting<SettingColor> colorEnd = sgRender.add(new ColorSetting.Builder().name("color-end").defaultValue(new SettingColor(0, 100, 255, 255)).visible(useSecondColor::get).build());
    private final Setting<Boolean> smoothFade = sgRender.add(new BoolSetting.Builder().name("smooth-fade").defaultValue(true).build());

    private final List<MatrixPulse> pulses = Collections.synchronizedList(new ArrayList<>());
    private boolean wasOnGround = true;

    public MoidJumpMatrix() {
        super(AddonTemplate.CATEGORY, "moid-jump-matrix", "The complete vibecoded matrix jump effect.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        boolean isJumping = !onlyJump.get() || mc.options.jumpKey.isPressed();
        if (wasOnGround && !mc.player.isOnGround() && mc.player.getVelocity().y > 0 && isJumping) {
            pulses.add(new MatrixPulse(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        }
        wasOnGround = mc.player.isOnGround();
        pulses.removeIf(p -> p.age > 1.0);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (pulses) {
            for (MatrixPulse p : pulses) p.render(event);
        }
    }

    private class MatrixPulse {
        private final Vec3d origin;
        public double age = 0;

        public MatrixPulse(Vec3d origin) { this.origin = origin; }

        public void render(Render3DEvent event) {
            age += (scanSpeed.get() * 0.01);
            if (age > 1.0) return;

            double currentRadius = age * maxRadius.get();
            int r = (int) Math.ceil(maxRadius.get());

            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos surface = mc.world.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, BlockPos.ofFloored(origin.x + x, origin.y, origin.z + z));
                    BlockPos bp = surface.down();
                    BlockState state = mc.world.getBlockState(bp);
                    if (state.isAir() || state.isReplaceable()) continue;

                    double dist = Math.sqrt(Math.pow(origin.x - (bp.getX() + 0.5), 2) + Math.pow(origin.z - (bp.getZ() + 0.5), 2));
                    double fadeWidth = smoothFade.get() ? currentRadius : 0.8;
                    
                    if (dist < currentRadius && dist > currentRadius - fadeWidth) {
                        float progress = (float) (dist / maxRadius.get());
                        double distanceFactor = smoothFade.get() ? (1.0 - ((currentRadius - dist) / currentRadius)) : 1.0;
                        int alpha = (int) (colorStart.get().a * (1.0 - age) * distanceFactor);
                        if (alpha <= 5) continue;

                        SettingColor cS = colorStart.get();
                        SettingColor cE = useSecondColor.get() ? colorEnd.get() : cS;
                        SettingColor drawCol = new SettingColor((int)(cS.r+(cE.r-cS.r)*progress), (int)(cS.g+(cE.g-cS.g)*progress), (int)(cS.b+(cE.b-cS.b)*progress), alpha);
                        
                        drawBlockEdges(event, bp, drawCol);
                    }
                }
            }
        }

        private void drawBlockEdges(Render3DEvent event, BlockPos p, SettingColor col) {
            double x1 = p.getX(), y1 = p.getY() + 1.01, z1 = p.getZ();
            double x2 = x1 + 1, z2 = z1 + 1;
            
            for (int i = 0; i < thickness.get(); i++) {
                double o = i * 0.002;
                event.renderer.line(x1-o, y1, z1-o, x2+o, y1, z1-o, col);
                event.renderer.line(x1-o, y1, z1-o, x1-o, y1, z2+o, col);
                event.renderer.line(x2+o, y1, z1-o, x2+o, y1, z2+o, col);
                event.renderer.line(x1-o, y1, z2+o, x2+o, y1, z2+o, col);
            }
        }
    }
}
