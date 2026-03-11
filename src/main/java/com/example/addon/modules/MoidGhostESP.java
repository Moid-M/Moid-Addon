package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public class MoidGhostESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");
    private final SettingGroup sgTrail = settings.createGroup("Trail");

    public enum GhostShape { Box, Circle, Triangle, Cross }

    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<GhostShape> shape = sg.add(new EnumSetting.Builder<GhostShape>().name("shape").defaultValue(GhostShape.Circle).build());
    private final Setting<Integer> amount = sg.add(new IntSetting.Builder().name("soul-amount").defaultValue(3).min(1).max(10).build());
    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder().name("speed").defaultValue(2.0).min(0).sliderMax(10).build());
    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(0.7).min(0.1).sliderMax(2.0).build());
    private final Setting<Double> size = sg.add(new DoubleSetting.Builder().name("soul-size").defaultValue(0.05).min(0.01).sliderMax(0.5).build());
    private final Setting<Double> vOffset = sg.add(new DoubleSetting.Builder().name("height-offset").defaultValue(0.5).min(-1).sliderMax(3).build());

    private final Setting<SettingColor> color = sgVisuals.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(150, 0, 255, 200)).build());
    private final Setting<Boolean> fill = sgVisuals.add(new BoolSetting.Builder().name("fill").defaultValue(true).build());
    private final Setting<Integer> thickness = sgVisuals.add(new IntSetting.Builder().name("thickness").defaultValue(1).min(1).max(10).visible(() -> !fill.get()).build());
    private final Setting<Double> wobble = sgVisuals.add(new DoubleSetting.Builder().name("wobble").defaultValue(0.2).min(0).sliderMax(1.0).build());

    private final Setting<Boolean> drawTrail = sgTrail.add(new BoolSetting.Builder().name("enable-trail").defaultValue(true).build());
    private final Setting<Integer> trailLength = sgTrail.add(new IntSetting.Builder().name("trail-length").defaultValue(20).min(2).sliderMax(100).visible(drawTrail::get).build());

    private final Map<UUID, List<List<Vec3d>>> trailMap = new HashMap<>();

    public MoidGhostESP() {
        super(AddonTemplate.CATEGORY, "moid-ghost-esp", "Premium orbital ESP with fading motion trails.");
    }

    @Override
    public void onDeactivate() {
        trailMap.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        double time = (System.currentTimeMillis() / 1000.0) * speed.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;

            Vec3d pos = entity.getLerpedPos(event.tickDelta);
            Box box = entity.getBoundingBox();
            double heightOffset = (box.maxY - box.minY) * vOffset.get();
            
            List<List<Vec3d>> entityTrails = trailMap.computeIfAbsent(entity.getUuid(), k -> new ArrayList<>());

            for (int i = 0; i < amount.get(); i++) {
                if (entityTrails.size() <= i) entityTrails.add(new LinkedList<>());
                List<Vec3d> trail = entityTrails.get(i);

                double orbitOffset = (i * (Math.PI * 2 / amount.get()));
                double orbitTime = time + orbitOffset;

                double ox = pos.x + Math.cos(orbitTime) * radius.get();
                double oz = pos.z + Math.sin(orbitTime) * radius.get();
                double oy = pos.y + heightOffset + Math.sin(orbitTime + orbitOffset) * wobble.get();
                Vec3d currentSoulPos = new Vec3d(ox, oy, oz);

                // Update Trail
                if (drawTrail.get()) {
                    trail.add(0, currentSoulPos);
                    while (trail.size() > trailLength.get()) trail.remove(trail.size() - 1);
                    renderTrail(event, trail, color.get());
                }

                renderSoul(event, ox, oy, oz, color.get());
            }
        }
    }

    private void renderTrail(Render3DEvent event, List<Vec3d> trail, SettingColor col) {
        for (int i = 0; i < trail.size() - 1; i++) {
            Vec3d p1 = trail.get(i);
            Vec3d p2 = trail.get(i + 1);
            
            // Fade logic: Alpha decreases as 'i' increases
            double pc = 1.0 - ((double) i / trail.size());
            int alpha = (int) (col.a * pc);
            SettingColor fadeCol = new SettingColor(col.r, col.g, col.b, alpha);

            event.renderer.line(p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, fadeCol);
        }
    }

    private void renderSoul(Render3DEvent event, double x, double y, double z, SettingColor col) {
        double s = size.get();
        ShapeMode mode = fill.get() ? ShapeMode.Both : ShapeMode.Lines;
        
        if (shape.get() == GhostShape.Box) {
            event.renderer.box(x - s, y - s, z - s, x + s, y + s, z + s, col, col, mode, 0);
        } else {
            double yaw = Math.toRadians(mc.getEntityRenderDispatcher().camera.getYaw());
            int loops = fill.get() ? 1 : thickness.get();
            for (int t = 0; t < loops; t++) {
                double o = t * 0.005;
                switch (shape.get()) {
                    case Circle -> drawCircle(event, x, y, z, s + o, col, yaw);
                    case Triangle -> drawTriangle(event, x, y, z, s + o, col, yaw);
                    case Cross -> drawCross(event, x, y, z, s + o, col);
                }
            }
        }
    }

    private void drawCircle(Render3DEvent event, double x, double y, double z, double s, SettingColor col, double yaw) {
        int segments = 12;
        for (int i = 0; i < segments; i++) {
            double a1 = Math.toRadians(i * (360.0 / segments));
            double a2 = Math.toRadians((i + 1) * (360.0 / segments));
            event.renderer.line(x + Math.cos(a1)*s*Math.cos(yaw), y + Math.sin(a1)*s, z + Math.cos(a1)*s*Math.sin(yaw),
                                x + Math.cos(a2)*s*Math.cos(yaw), y + Math.sin(a2)*s, z + Math.cos(a2)*s*Math.sin(yaw), col);
        }
    }

    private void drawTriangle(Render3DEvent event, double x, double y, double z, double s, SettingColor col, double yaw) {
        for (int i = 0; i < 3; i++) {
            double a1 = Math.toRadians(i * 120);
            double a2 = Math.toRadians((i + 1) * 120);
            event.renderer.line(x + Math.cos(a1)*s*Math.cos(yaw), y + Math.sin(a1)*s, z + Math.cos(a1)*s*Math.sin(yaw),
                                x + Math.cos(a2)*s*Math.cos(yaw), y + Math.sin(a2)*s, z + Math.cos(a2)*s*Math.sin(yaw), col);
        }
    }

    private void drawCross(Render3DEvent event, double x, double y, double z, double s, SettingColor col) {
        event.renderer.line(x - s, y, z, x + s, y, z, col);
        event.renderer.line(x, y - s, z, x, y + s, z, col);
        event.renderer.line(x, y, z - s, x, y, z + s, col);
    }
}
