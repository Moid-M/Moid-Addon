package com.moid.addon.modules;

import com.moid.addon.MoidAddon;
import com.moid.addon.utils.ColorUtils;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MoidVelocity extends Module {

    private final SettingGroup sgGeneral  = settings.getDefaultGroup();
    private final SettingGroup sgAdvanced = settings.createGroup("Advanced");
    private final SettingGroup sgRender   = settings.createGroup("Render");

    public enum Mode { Vanilla, Packet, Delay }

    // --- General ---
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Vanilla = reduce kb | Packet = cancel | Delay = lag simulation")
        .defaultValue(Mode.Vanilla)
        .build()
    );

    private final Setting<Integer> horizontal = sgGeneral.add(new IntSetting.Builder()
        .name("horizontal")
        .description("Horizontal knockback % (0 = none, 100 = full)")
        .defaultValue(0).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Integer> vertical = sgGeneral.add(new IntSetting.Builder()
        .name("vertical")
        .description("Vertical knockback % (0 = none, 100 = full)")
        .defaultValue(0).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Integer> delayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Ticks to delay kb (20 = 1 second)")
        .defaultValue(20).min(1).max(100).sliderRange(1, 100)
        .visible(() -> mode.get() == Mode.Delay)
        .build()
    );

    // --- Advanced ---
    private final Setting<Boolean> useMinMaxChance = sgAdvanced.add(new BoolSetting.Builder()
        .name("min-max-chance")
        .description("Randomize chance between min and max each hit")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> minChance = sgAdvanced.add(new IntSetting.Builder()
        .name("min-chance")
        .description("Minimum chance % to apply")
        .defaultValue(60).min(1).max(100).sliderRange(1, 100)
        .visible(() -> useMinMaxChance.get())
        .build()
    );

    private final Setting<Integer> maxChance = sgAdvanced.add(new IntSetting.Builder()
        .name("max-chance")
        .description("Maximum chance % to apply")
        .defaultValue(90).min(1).max(100).sliderRange(1, 100)
        .visible(() -> useMinMaxChance.get())
        .build()
    );

    private final Setting<Integer> chance = sgAdvanced.add(new IntSetting.Builder()
        .name("chance")
        .description("Chance % to apply (100 = always)")
        .defaultValue(100).min(1).max(100).sliderRange(1, 100)
        .visible(() -> !useMinMaxChance.get())
        .build()
    );

    private final Setting<Boolean> useMinMax = sgAdvanced.add(new BoolSetting.Builder()
        .name("min-max-kb")
        .description("Randomize horizontal/vertical between min and max each hit")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Vanilla)
        .build()
    );

    private final Setting<Integer> minHorizontal = sgAdvanced.add(new IntSetting.Builder()
        .name("min-horizontal")
        .description("Minimum horizontal knockback %")
        .defaultValue(0).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla && useMinMax.get())
        .build()
    );

    private final Setting<Integer> maxHorizontal = sgAdvanced.add(new IntSetting.Builder()
        .name("max-horizontal")
        .description("Maximum horizontal knockback %")
        .defaultValue(30).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla && useMinMax.get())
        .build()
    );

    private final Setting<Integer> minVertical = sgAdvanced.add(new IntSetting.Builder()
        .name("min-vertical")
        .description("Minimum vertical knockback %")
        .defaultValue(0).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla && useMinMax.get())
        .build()
    );

    private final Setting<Integer> maxVertical = sgAdvanced.add(new IntSetting.Builder()
        .name("max-vertical")
        .description("Maximum vertical knockback %")
        .defaultValue(50).min(0).max(100).sliderRange(0, 100)
        .visible(() -> mode.get() == Mode.Vanilla && useMinMax.get())
        .build()
    );

    private final Setting<Boolean> useMinMaxDelay = sgAdvanced.add(new BoolSetting.Builder()
        .name("min-max-delay")
        .description("Randomize delay between min and max ticks each hit")
        .defaultValue(false)
        .visible(() -> mode.get() == Mode.Delay)
        .build()
    );

    private final Setting<Integer> minDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("min-delay")
        .description("Minimum delay ticks")
        .defaultValue(10).min(1).max(100).sliderRange(1, 100)
        .visible(() -> mode.get() == Mode.Delay && useMinMaxDelay.get())
        .build()
    );

    private final Setting<Integer> maxDelay = sgAdvanced.add(new IntSetting.Builder()
        .name("max-delay")
        .description("Maximum delay ticks")
        .defaultValue(40).min(1).max(100).sliderRange(1, 100)
        .visible(() -> mode.get() == Mode.Delay && useMinMaxDelay.get())
        .build()
    );

    // --- Render ---
    private final Setting<Boolean> showBox = sgRender.add(new BoolSetting.Builder()
        .name("show-box")
        .description("Show smoothly moving server-side position box during delay")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Delay)
        .build()
    );

    private final Setting<ColorUtils.ColorMode> colorMode = sgRender.add(new EnumSetting.Builder<ColorUtils.ColorMode>()
        .name("color-mode")
        .description("Static = fixed color | Fade = fades as delay runs out")
        .defaultValue(ColorUtils.ColorMode.Fade)
        .visible(() -> mode.get() == Mode.Delay && showBox.get())
        .build()
    );

    private final Setting<Boolean> fadeAlpha = sgRender.add(new BoolSetting.Builder()
        .name("fade-alpha")
        .description("Fade alpha as delay runs out")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Delay && showBox.get())
        .build()
    );

    private final Setting<SettingColor> mainColor = sgRender.add(new ColorSetting.Builder()
        .name("main-color")
        .defaultValue(new SettingColor(255, 50, 50, 100))
        .visible(() -> mode.get() == Mode.Delay && showBox.get())
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .defaultValue(new SettingColor(255, 50, 50, 200))
        .visible(() -> mode.get() == Mode.Delay && showBox.get())
        .build()
    );

    private final Setting<SettingColor> fadeColor = sgRender.add(new ColorSetting.Builder()
        .name("fade-to-color")
        .defaultValue(new SettingColor(255, 150, 50, 100))
        .visible(() -> mode.get() == Mode.Delay && showBox.get()
            && colorMode.get() == ColorUtils.ColorMode.Fade)
        .build()
    );

    private final Setting<SettingColor> lineFadeColor = sgRender.add(new ColorSetting.Builder()
        .name("line-fade-to")
        .defaultValue(new SettingColor(255, 150, 50, 200))
        .visible(() -> mode.get() == Mode.Delay && showBox.get()
            && colorMode.get() == ColorUtils.ColorMode.Fade)
        .build()
    );

    private static class QueuedPacket {
        final long applyAtTick;
        final long queuedAtMs;
        final long durationMs;
        final Packet<?> packet;
        final List<Vec3d> path;

        QueuedPacket(long applyAtTick, Packet<?> packet,
                     List<Vec3d> path, long durationMs) {
            this.applyAtTick = applyAtTick;
            this.packet      = packet;
            this.path        = path;
            this.queuedAtMs  = System.currentTimeMillis();
            this.durationMs  = durationMs;
        }

        Vec3d getSmoothedPos(float progress) {
            if (path == null || path.isEmpty()) return null;
            if (path.size() == 1) return path.get(0);
            float scaled = progress * (path.size() - 1);
            int idx      = (int) scaled;
            float frac   = scaled - idx;
            if (idx >= path.size() - 1) return path.get(path.size() - 1);
            Vec3d a = path.get(idx);
            Vec3d b = path.get(idx + 1);
            return new Vec3d(
                MathHelper.lerp(frac, a.x, b.x),
                MathHelper.lerp(frac, a.y, b.y),
                MathHelper.lerp(frac, a.z, b.z)
            );
        }
    }

    private final List<QueuedPacket> queue = new ArrayList<>();
    private final Random random            = new Random();
    private long tickCount                 = 0;
    private boolean replaying              = false;
    private boolean vanillaPending         = false;
    private double pendingH                = 0;
    private double pendingV                = 0;

    // Delay mode — capture kb velocity after packet applies
    private boolean capturePending        = false;
    private Vec3d preKbVelocity           = null;
    private Vec3d preKbPos                = null;
    private Packet<?> pendingDelayPacket  = null;
    private int pendingDelayTicks         = 0;

    public MoidVelocity() {
        super(MoidAddon.CATEGORY, "moid-velocity",
            "Modifies the knockback you receive");
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        tickCount       = 0;
        replaying       = false;
        vanillaPending  = false;
        capturePending  = false;
        preKbVelocity   = null;
        preKbPos        = null;
        pendingDelayPacket = null;
    }

    private boolean rollChance() {
        if (useMinMaxChance.get()) {
            int min = minChance.get();
            int max = maxChance.get();
            if (max < min) max = min;
            int threshold = min + random.nextInt(max - min + 1);
            return random.nextInt(100) < threshold;
        }
        return random.nextInt(100) < chance.get();
    }

    private double getH() {
        if (!useMinMax.get()) return horizontal.get() / 100.0;
        int min = minHorizontal.get();
        int max = maxHorizontal.get();
        if (max < min) max = min;
        return (min + random.nextInt(max - min + 1)) / 100.0;
    }

    private double getV() {
        if (!useMinMax.get()) return vertical.get() / 100.0;
        int min = minVertical.get();
        int max = maxVertical.get();
        if (max < min) max = min;
        return (min + random.nextInt(max - min + 1)) / 100.0;
    }

    private int getDelay() {
        if (!useMinMaxDelay.get()) return delayTicks.get();
        int min = minDelay.get();
        int max = maxDelay.get();
        if (max < min) max = min;
        return min + random.nextInt(max - min + 1);
    }

    private List<Vec3d> simulatePath(Vec3d startPos,
                                     double kbX, double kbY, double kbZ,
                                     int ticks) {
        List<Vec3d> path = new ArrayList<>();
        double x  = startPos.x;
        double y  = startPos.y;
        double z  = startPos.z;
        double vx = kbX;
        double vy = kbY;
        double vz = kbZ;

        double friction = 0.91;
        double gravity  = 0.08;
        double drag     = 0.98;

        for (int i = 0; i < ticks; i++) {
            x  += vx;
            y  += vy;
            z  += vz;
            vx *= friction;
            vz *= friction;
            vy -= gravity;
            vy *= drag;
            path.add(new Vec3d(x, Math.max(startPos.y, y), z));
        }
        return path;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        tickCount++;

        // Capture kb velocity AFTER packet applied this tick
        if (capturePending && preKbVelocity != null && preKbPos != null) {
            capturePending = false;

            // This is the REAL kb velocity the packet applied
            Vec3d kbVel = mc.player.getVelocity();
            double kbX  = kbVel.x - preKbVelocity.x;
            double kbY  = kbVel.y - preKbVelocity.y;
            double kbZ  = kbVel.z - preKbVelocity.z;

            // Restore velocity to pre-kb state
            mc.player.setVelocity(preKbVelocity);

            // Now simulate path using real kb delta
            List<Vec3d> path = simulatePath(
                preKbPos, kbX, kbY, kbZ, pendingDelayTicks);

            long durationMs = (long)(pendingDelayTicks * 50L);
            queue.add(new QueuedPacket(
                tickCount + pendingDelayTicks,
                pendingDelayPacket, path, durationMs));

            preKbVelocity   = null;
            preKbPos        = null;
            pendingDelayPacket = null;
        }

        if (mode.get() != Mode.Delay) return;

        // Release ready packets
        List<QueuedPacket> ready = new ArrayList<>();
        for (QueuedPacket qp : queue) {
            if (tickCount >= qp.applyAtTick) ready.add(qp);
        }
        for (QueuedPacket qp : ready) {
            queue.remove(qp);
            replaying = true;
            mc.getNetworkHandler().onEntityVelocityUpdate(
                (EntityVelocityUpdateS2CPacket) qp.packet);
            replaying = false;
        }
    }

    @EventHandler
    private void onTickVanilla(TickEvent.Pre event) {
        if (!vanillaPending || mc.player == null) return;
        vanillaPending = false;
        double x = mc.player.getVelocity().x;
        double y = mc.player.getVelocity().y;
        double z = mc.player.getVelocity().z;
        mc.player.setVelocity(
            x * pendingH,
            y * pendingV,
            z * pendingH
        );
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (replaying) return;
        if (!(event.packet instanceof EntityVelocityUpdateS2CPacket packet)) return;
        if (mc.player == null) return;
        if (packet.getEntityId() != mc.player.getId()) return;

        if (!rollChance()) return;

        switch (mode.get()) {
            case Packet -> event.cancel();

            case Vanilla -> {
                pendingH       = getH();
                pendingV       = getV();
                vanillaPending = true;
            }

            case Delay -> {
                // Don't cancel — let packet apply so we can read real kb
                // Snapshot velocity and position BEFORE packet applies
                preKbVelocity    = mc.player.getVelocity();
                preKbPos         = new Vec3d(
                    mc.player.getX(),
                    mc.player.getY(),
                    mc.player.getZ()
                );
                pendingDelayPacket = packet;
                pendingDelayTicks  = getDelay();
                capturePending     = true;
                // Cancel so kb doesn't apply yet
                event.cancel();
                // But we still need the packet to apply to read velocity
                // So replay it immediately just for velocity reading
                replaying = true;
                mc.getNetworkHandler().onEntityVelocityUpdate(packet);
                replaying = false;
                // Velocity is now set by packet — onTick will
                // read it, restore it, and queue the delayed packet
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mode.get() != Mode.Delay) return;
        if (!showBox.get()) return;
        if (queue.isEmpty()) return;

        for (QueuedPacket qp : queue) {
            if (qp.path == null || qp.path.isEmpty()) continue;

            float progress = (float) MathHelper.clamp(
                (double)(System.currentTimeMillis() - qp.queuedAtMs)
                    / qp.durationMs, 0, 1);

            Vec3d pos = qp.getSmoothedPos(progress);
            if (pos == null) continue;

            Color side = (colorMode.get() == ColorUtils.ColorMode.Fade)
                ? ColorUtils.lerpColor(mainColor.get(), fadeColor.get(), progress)
                : new Color(mainColor.get());
            Color line = (colorMode.get() == ColorUtils.ColorMode.Fade)
                ? ColorUtils.lerpColor(lineColor.get(), lineFadeColor.get(), progress)
                : new Color(lineColor.get());

            if (fadeAlpha.get()) {
                int alphaMult = (int)(255 * (1.0 - progress));
                side.a = (side.a * alphaMult) / 255;
                line.a = (line.a * alphaMult) / 255;
            }

            event.renderer.box(
                pos.x - 0.3, pos.y, pos.z - 0.3,
                pos.x + 0.3, pos.y + 1.8, pos.z + 0.3,
                side, line, ShapeMode.Both, 0
            );
        }
    }

}