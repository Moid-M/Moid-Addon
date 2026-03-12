package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MoidHitESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Shape { Square, Circle, Triangle, Cross, Logo }
    public enum Mode { Flat, Dynamic }

    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Shape> shape = sg.add(new EnumSetting.Builder<Shape>().name("shape").defaultValue(Shape.Square).build());
    private final Setting<Mode> renderMode = sg.add(new EnumSetting.Builder<Mode>().name("render-mode").defaultValue(Mode.Dynamic).build());
    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder().name("duration-secs").defaultValue(1.0).min(0.1).sliderMax(5).build());
    private final Setting<Double> size = sg.add(new DoubleSetting.Builder().name("size").defaultValue(0.5).min(0.1).sliderMax(2).build());
    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(5).build());
    private final Setting<Double> spinSpeed = sg.add(new DoubleSetting.Builder().name("spin-speed").defaultValue(5.0).min(0).sliderMax(20).build());
    private final Setting<Double> vOffset = sg.add(new DoubleSetting.Builder().name("height-offset").defaultValue(1.0).min(-1).sliderMax(3).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 255, 255, 200)).build());

    private final List<HitParticle> hits = new ArrayList<>();

    public MoidHitESP() {
        super(AddonTemplate.CATEGORY, "moid-hit-esp", "Billboarded impact geometry.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (entities.get().contains(event.entity.getType())) {
            hits.add(new HitParticle(event.entity));
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (hits) {
            hits.removeIf(h -> h.isDead());
            for (HitParticle h : hits) h.render(event);
        }
    }

    private class HitParticle {
        private final Entity target;
        private final long startTime;
        private final double maxAge;

        public HitParticle(Entity target) {
            this.target = target;
            this.startTime = System.currentTimeMillis();
            this.maxAge = duration.get() * 1000;
        }

        public boolean isDead() { return System.currentTimeMillis() - startTime > maxAge; }

        public void render(Render3DEvent event) {
            double age = (double) (System.currentTimeMillis() - startTime) / maxAge;
            double x = MathHelper.lerp(event.tickDelta, target.lastRenderX, target.getX());
            double y = MathHelper.lerp(event.tickDelta, target.lastRenderY, target.getY()) + vOffset.get();
            double z = MathHelper.lerp(event.tickDelta, target.lastRenderZ, target.getZ());

            double currentSize = size.get() * (1.0 - (age * 0.3)); 
            int alpha = (int) (color.get().a * (1.0 - age));
            Color drawCol = new Color(color.get().r, color.get().g, color.get().b, alpha);
            double rotation = (System.currentTimeMillis() / 100.0) * spinSpeed.get();

            if (shape.get() == Shape.Logo) {
                drawGeometricLogo(event, new Vec3d(x, y, z), new Vec3d(event.offsetX, event.offsetY, event.offsetZ), currentSize, rotation, drawCol);
            } else if (renderMode.get() == Mode.Dynamic) {
                drawDynamic(event, new Vec3d(x, y, z), new Vec3d(event.offsetX, event.offsetY, event.offsetZ), currentSize, rotation, drawCol);
            } else {
                drawFlat(event, x, y, z, currentSize, rotation, drawCol);
            }
        }

        private void drawGeometricLogo(Render3DEvent event, Vec3d pos, Vec3d cam, double s, double r, Color col) {
            // Draws a custom geometric "Moid" symbol that always faces the camera
            Vec3d dir = cam.subtract(pos).normalize();
            Vec3d right = new Vec3d(0, 1, 0).crossProduct(dir).normalize();
            Vec3d up = dir.crossProduct(right).normalize();

            double rad = Math.toRadians(r);
            Vec3d rotatedRight = right.multiply(Math.cos(rad)).add(up.multiply(Math.sin(rad)));
            Vec3d rotatedUp = up.multiply(Math.cos(rad)).subtract(right.multiply(Math.sin(rad)));

            // Outer Diamond
            Vec3d top = pos.add(rotatedUp.multiply(s));
            Vec3d bottom = pos.subtract(rotatedUp.multiply(s));
            Vec3d left = pos.subtract(rotatedRight.multiply(s));
            Vec3d rightP = pos.add(rotatedRight.multiply(s));

            event.renderer.line(top.x, top.y, top.z, rightP.x, rightP.y, rightP.z, col);
            event.renderer.line(rightP.x, rightP.y, rightP.z, bottom.x, bottom.y, bottom.z, col);
            event.renderer.line(bottom.x, bottom.y, bottom.z, left.x, left.y, left.z, col);
            event.renderer.line(left.x, left.y, left.z, top.x, top.y, top.z, col);

            // Inner Cross
            event.renderer.line(top.x, top.y, top.z, bottom.x, bottom.y, bottom.z, col);
            event.renderer.line(left.x, left.y, left.z, rightP.x, rightP.y, rightP.z, col);
        }

        private void drawFlat(Render3DEvent event, double x, double y, double z, double s, double r, Color col) {
            int segments = getSegments();
            for (int t = 0; t < thickness.get(); t++) {
                double off = t * 0.01;
                for (int i = 0; i < segments; i++) {
                    double a1 = Math.toRadians((360.0 / segments) * i) + r;
                    double a2 = Math.toRadians((360.0 / segments) * (i + 1)) + r;
                    if (shape.get() == Shape.Cross) {
                        event.renderer.line(x - Math.cos(a1)*s, y, z - Math.sin(a1)*s, x + Math.cos(a1)*s, y, z + Math.sin(a1)*s, col);
                        event.renderer.line(x - Math.sin(a1)*s, y, z + Math.cos(a1)*s, x + Math.sin(a1)*s, y, z - Math.cos(a1)*s, col);
                    } else {
                        event.renderer.line(x + Math.cos(a1)*(s+off), y, z + Math.sin(a1)*(s+off), x + Math.cos(a2)*(s+off), y, z + Math.sin(a2)*(s+off), col);
                    }
                }
            }
        }

        private void drawDynamic(Render3DEvent event, Vec3d pos, Vec3d cam, double s, double r, Color col) {
            Vec3d dir = cam.subtract(pos).normalize();
            Vec3d right = new Vec3d(0, 1, 0).crossProduct(dir).normalize();
            Vec3d up = dir.crossProduct(right).normalize();

            int segments = getSegments();
            for (int t = 0; t < thickness.get(); t++) {
                double off = t * 0.01;
                for (int i = 0; i < segments; i++) {
                    double a1 = Math.toRadians((360.0 / segments) * i) + r;
                    double a2 = Math.toRadians((360.0 / segments) * (i + 1)) + r;

                    Vec3d p1 = pos.add(right.multiply(Math.cos(a1) * (s+off))).add(up.multiply(Math.sin(a1) * (s+off)));
                    Vec3d p2 = pos.add(right.multiply(Math.cos(a2) * (s+off))).add(up.multiply(Math.sin(a2) * (s+off)));

                    if (shape.get() == Shape.Cross) {
                        Vec3d c1 = pos.add(right.multiply(Math.cos(a1) * s)).add(up.multiply(Math.sin(a1) * s));
                        Vec3d c2 = pos.subtract(right.multiply(Math.cos(a1) * s)).subtract(up.multiply(Math.sin(a1) * s));
                        event.renderer.line(c1.x, c1.y, c1.z, c2.x, c2.y, c2.z, col);
                    } else {
                        event.renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, col);
                    }
                }
            }
        }

        private int getSegments() {
            return switch (shape.get()) {
                case Triangle -> 3;
                case Square -> 4;
                case Cross -> 4;
                default -> 24;
            };
        }
    }
}