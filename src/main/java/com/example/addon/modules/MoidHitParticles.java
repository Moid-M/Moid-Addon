package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MoidHitParticles extends Module {
    public enum Shape { Square, Star, Heart, Circle, Triangle }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Shape> shape = sg.add(new EnumSetting.Builder<Shape>().name("shape").defaultValue(Shape.Star).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 0, 0, 255)).build());
    private final Setting<Boolean> fill = sg.add(new BoolSetting.Builder().name("fill").defaultValue(true).build());
    private final Setting<Boolean> glow = sg.add(new BoolSetting.Builder().name("glow").defaultValue(true).build());
    private final Setting<Double> size = sg.add(new DoubleSetting.Builder().name("size").defaultValue(0.15).min(0.01).sliderMax(0.5).build());
    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(0.2).min(0.01).sliderMax(1.0).build());
    private final Setting<Double> rotationSpeed = sg.add(new DoubleSetting.Builder().name("rotation-speed").defaultValue(5.0).min(0).sliderMax(20.0).build());
    private final Setting<Integer> amount = sg.add(new IntSetting.Builder().name("amount").defaultValue(10).min(1).sliderMax(50).build());
    private final Setting<Integer> lifeTime = sg.add(new IntSetting.Builder().name("life-time").defaultValue(40).min(1).sliderMax(100).build());

    private final List<Particle> particles = new ArrayList<>();
    private final Color renderColor = new Color();

    public MoidHitParticles() {
        super(AddonTemplate.CATEGORY, "moid-hit-particles", "Camera-facing 2D particles with Glow.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (target == null || !entities.get().contains(target.getType())) return;
        Vec3d pos = new Vec3d(target.getX(), target.getY() + (target.getEyeHeight(target.getPose()) / 1.5), target.getZ());
        synchronized (particles) {
            for (int i = 0; i < amount.get(); i++) particles.add(new Particle(pos));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        synchronized (particles) {
            particles.removeIf(p -> {
                p.lastPos = p.pos;
                p.pos = p.pos.add(p.vel);
                p.vel = new Vec3d(p.vel.x, p.vel.y - 0.003, p.vel.z);
                p.rotation += rotationSpeed.get();
                return --p.life <= 0;
            });
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (particles) {
            for (Particle p : particles) {
                float alpha = (float) p.life / lifeTime.get();
                double x = p.lastPos.x + (p.pos.x - p.lastPos.x) * event.tickDelta;
                double y = p.lastPos.y + (p.pos.y - p.lastPos.y) * event.tickDelta;
                double z = p.lastPos.z + (p.pos.z - p.lastPos.z) * event.tickDelta;
                double s = size.get() * alpha;

                // Draw Glow first
                if (glow.get()) {
                    renderColor.set(color.get());
                    renderColor.a = (int) (color.get().a * alpha * 0.3);
                    drawBillboard(event, x, y, z, s * 1.5, p.rotation, true);
                }

                // Draw Main Shape
                renderColor.set(color.get());
                renderColor.a = (int) (color.get().a * alpha);
                drawBillboard(event, x, y, z, s, p.rotation, fill.get());
            }
        }
    }

    private void drawBillboard(Render3DEvent event, double x, double y, double z, double s, double rotation, boolean isFilled) {
        double yaw = Math.toRadians(mc.getEntityRenderDispatcher().camera.getYaw());
        double pitch = Math.toRadians(mc.getEntityRenderDispatcher().camera.getPitch());
        Vec3d right = new Vec3d(Math.cos(yaw), 0, Math.sin(yaw));
        Vec3d up = new Vec3d(Math.sin(yaw) * Math.sin(pitch), Math.cos(pitch), -Math.cos(yaw) * Math.sin(pitch));

        int points = switch (shape.get()) {
            case Star -> 5;
            case Triangle -> 3;
            case Circle -> 16;
            case Heart -> 4;
            default -> 4;
        };

        double step = 360.0 / points;
        Vec3d last = null;
        Vec3d first = null;

        for (int i = 0; i <= points; i++) {
            double angle = Math.toRadians(i * (shape.get() == Shape.Star ? 144 : step) + rotation);
            double rx = Math.cos(angle) * s;
            double ry = Math.sin(angle) * s;

            Vec3d current = new Vec3d(x, y, z).add(right.multiply(rx)).add(up.multiply(ry));

            if (last != null) {
                if (isFilled) event.renderer.quad(x, y, z, x, y, z, last.x, last.y, last.z, current.x, current.y, current.z, renderColor);
                event.renderer.line(last.x, last.y, last.z, current.x, current.y, current.z, renderColor);
            }
            if (i == 0) first = current;
            last = current;
        }
    }

    private class Particle {
        Vec3d pos, lastPos, vel;
        int life;
        double rotation;
        public Particle(Vec3d origin) {
            this.pos = origin; this.lastPos = origin;
            double r = range.get();
            this.vel = new Vec3d((ThreadLocalRandom.current().nextDouble() - 0.5) * r, ThreadLocalRandom.current().nextDouble() * 0.1, (ThreadLocalRandom.current().nextDouble() - 0.5) * r);
            this.life = lifeTime.get();
            this.rotation = ThreadLocalRandom.current().nextDouble() * 360;
        }
    }
}
