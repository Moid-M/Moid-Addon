package com.moid.addon.modules;

import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.moid.addon.MoidAddon;
import com.moid.addon.utils.ColorUtils;

public class MoidLag extends Module {

    public enum Mode { Static, Random, Smooth }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Mode> delayMode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("delay-mode")
        .description("Static = fixed delay | Random = random delay | Smooth = natural looking delay")
        .defaultValue(Mode.Static)
        .build());

    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder()
        .name("min-delay-ms")
        .defaultValue(200).min(0).sliderMax(1000)
        .build());

    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder()
        .name("max-delay-ms")
        .defaultValue(500).min(0).sliderMax(2000)
        .visible(() -> delayMode.get() == Mode.Random)
        .build());

    private final Setting<Boolean> fullLag = sgGeneral.add(new BoolSetting.Builder()
        .name("full-lag")
        .description("Delay ALL packets including keep-alive to raise actual ping")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> autoRelease = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-release")
        .defaultValue(true)
        .build());

    private final Setting<ColorUtils.ColorMode> colorMode = sgRender.add(new EnumSetting.Builder<ColorUtils.ColorMode>()
        .name("color-mode").defaultValue(ColorUtils.ColorMode.Fade).build());

    private final Setting<Boolean> fadeAlpha = sgRender.add(new BoolSetting.Builder()
        .name("fade-alpha").defaultValue(true).build());

    private final Setting<SettingColor> mainColor = sgRender.add(new ColorSetting.Builder()
        .name("main-color").defaultValue(new SettingColor(120, 120, 255, 100)).build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color").defaultValue(new SettingColor(120, 120, 255, 200)).build());

    private final Setting<SettingColor> fadeColor = sgRender.add(new ColorSetting.Builder()
        .name("fade-to-color").defaultValue(new SettingColor(255, 50, 50, 100))
        .visible(() -> colorMode.get() == ColorUtils.ColorMode.Fade).build());

    private final Setting<SettingColor> lineFadeColor = sgRender.add(new ColorSetting.Builder()
        .name("line-fade-to").defaultValue(new SettingColor(255, 50, 50, 200))
        .visible(() -> colorMode.get() == ColorUtils.ColorMode.Fade).build());

    // Standard packet queue for Static/Random modes
    private final List<Packet<?>> packets     = new CopyOnWriteArrayList<>();

    // Smooth mode — each packet has its own release time
    private static class TimedPacket {
        final Packet<?> packet;
        final long releaseAtMs;
        final Vec3d pos; // position when this packet was queued

        TimedPacket(Packet<?> packet, long releaseAtMs, Vec3d pos) {
            this.packet      = packet;
            this.releaseAtMs = releaseAtMs;
            this.pos         = pos;
        }
    }

    private final List<TimedPacket> smoothQueue = new CopyOnWriteArrayList<>();

    private final Random random       = new Random();
    private Vec3d ghostPos            = null;
    private Vec3d smoothGhostPos      = null; // current interpolated ghost
    private Vec3d smoothGhostTarget   = null; // next ghost target position
    private Vec3d smoothGhostPrev     = null; // previous ghost position
    private long smoothGhostMoveStart = 0;
    private long smoothGhostMoveDur   = 0;

    private long lastReleaseTime;
    private int currentTargetDelay;
    private boolean isReleasing;

    // Track time between packets for smooth spacing
    private long lastPacketQueueTime  = 0;

    public MoidLag() {
        super(MoidAddon.CATEGORY, "moid-lag",
            "FakeLag with smooth natural-looking delay mode.");
    }

    @Override
    public void onDeactivate() {
        // Release everything on disable
        if (mc.getNetworkHandler() != null) {
            packets.forEach(p -> mc.getNetworkHandler().sendPacket(p));
            smoothQueue.forEach(tp -> mc.getNetworkHandler().sendPacket(tp.packet));
        }
        packets.clear();
        smoothQueue.clear();
        ghostPos          = null;
        smoothGhostPos    = null;
        smoothGhostTarget = null;
        smoothGhostPrev   = null;
    }

    private void resetTimer() {
        lastReleaseTime = System.currentTimeMillis();
        int min = minDelay.get();
        int max = maxDelay.get();
        currentTargetDelay = (delayMode.get() == Mode.Static || max <= min)
            ? min
            : min + random.nextInt(max - min + 1);
    }

    private void captureGhost() {
        if (mc.player == null) return;
        ghostPos = new Vec3d(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ());
    }

    private void releasePackets() {
        if (packets.isEmpty() || mc.getNetworkHandler() == null) return;
        isReleasing = true;
        packets.forEach(p -> mc.getNetworkHandler().sendPacket(p));
        packets.clear();
        isReleasing = false;
        resetTimer();
        captureGhost();
    }

    private boolean shouldDelay(Packet<?> p) {
        if (fullLag.get()) return true;
        return p instanceof PlayerMoveC2SPacket
            || p instanceof PlayerInputC2SPacket;
    }

    private boolean shouldAutoRelease(Packet<?> p) {
        if (!autoRelease.get()) return false;
        if (fullLag.get()) return false;
        return p instanceof PlayerInteractEntityC2SPacket
            || p instanceof PlayerActionC2SPacket
            || p instanceof PlayerInteractBlockC2SPacket;
    }

    private int getDelay() {
        int min = minDelay.get();
        int max = maxDelay.get();
        if (delayMode.get() == Mode.Random && max > min)
            return min + random.nextInt(max - min + 1);
        return min;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || isReleasing) return;
        Packet<?> p = event.packet;

        if (shouldAutoRelease(p)) {
            if (delayMode.get() == Mode.Smooth) {
                // Release all smooth packets immediately
                isReleasing = true;
                smoothQueue.forEach(tp ->
                    mc.getNetworkHandler().sendPacket(tp.packet));
                smoothQueue.clear();
                isReleasing = false;
            } else {
                releasePackets();
            }
            return;
        }

        if (!shouldDelay(p)) return;

        if (delayMode.get() == Mode.Smooth) {
            handleSmooth(event, p);
        } else {
            // Static / Random batch mode
            if (packets.isEmpty()) captureGhost();
            event.cancel();
            packets.add(p);
            if (System.currentTimeMillis() - lastReleaseTime >= currentTargetDelay) {
                releasePackets();
            }
        }
    }

    private void handleSmooth(PacketEvent.Send event, Packet<?> p) {
        event.cancel();

        long now     = System.currentTimeMillis();
        int delay    = getDelay();

        // Calculate when this packet should be released
        // Space packets evenly — if last packet was queued
        // recently, add a small gap so they don't all arrive at once
        long minReleaseAt = now + delay;
        if (!smoothQueue.isEmpty()) {
            // Find the latest scheduled release time
            long latestRelease = smoothQueue.stream()
                .mapToLong(tp -> tp.releaseAtMs)
                .max()
                .orElse(now);
            // Space by time since last packet was queued
            long timeSinceLast = now - lastPacketQueueTime;
            // Release this packet at latest + same gap as it arrived
            minReleaseAt = Math.max(minReleaseAt,
                latestRelease + Math.max(1, timeSinceLast));
        }

        // Capture current player position for ghost rendering
        Vec3d pos = new Vec3d(
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ());

        smoothQueue.add(new TimedPacket(p, minReleaseAt, pos));
        lastPacketQueueTime = now;

        // Update ghost target to smoothly move toward
        // the position of the earliest unreleased packet
        updateSmoothGhostTarget();
    }

    private void updateSmoothGhostTarget() {
        if (smoothQueue.isEmpty()) return;
        // Ghost should show where the server thinks we are —
        // the position of the FIRST (oldest) queued packet
        TimedPacket oldest = smoothQueue.get(0);
        if (oldest.pos == null) return;

        if (smoothGhostPos == null) {
            smoothGhostPos = oldest.pos;
        }

        // Only update target if it changed
        if (smoothGhostTarget == null ||
            !smoothGhostTarget.equals(oldest.pos)) {
            smoothGhostPrev      = smoothGhostPos;
            smoothGhostTarget    = oldest.pos;
            smoothGhostMoveStart = System.currentTimeMillis();
            // Move duration = time until this packet releases
            smoothGhostMoveDur   = Math.max(50,
                oldest.releaseAtMs - System.currentTimeMillis());
        }
    }

    private void tickSmoothQueue() {
        if (mc.getNetworkHandler() == null) return;
        long now = System.currentTimeMillis();

        // Release packets whose time has come
        List<TimedPacket> toRelease = new CopyOnWriteArrayList<>();
        for (TimedPacket tp : smoothQueue) {
            if (now >= tp.releaseAtMs) toRelease.add(tp);
        }

        if (!toRelease.isEmpty()) {
            isReleasing = true;
            toRelease.forEach(tp -> {
                mc.getNetworkHandler().sendPacket(tp.packet);
                smoothQueue.remove(tp);
            });
            isReleasing = false;
            updateSmoothGhostTarget();
        }

        // Interpolate ghost position smoothly
        if (smoothGhostPrev != null && smoothGhostTarget != null
            && smoothGhostMoveDur > 0) {
            float progress = MathHelper.clamp(
                (float)(now - smoothGhostMoveStart) / smoothGhostMoveDur,
                0f, 1f);
            // Smooth ease in/out
            float eased = progress < 0.5f
                ? 2f * progress * progress
                : 1f - (float)Math.pow(-2f * progress + 2f, 2) / 2f;
            smoothGhostPos = new Vec3d(
                MathHelper.lerp(eased, smoothGhostPrev.x, smoothGhostTarget.x),
                MathHelper.lerp(eased, smoothGhostPrev.y, smoothGhostTarget.y),
                MathHelper.lerp(eased, smoothGhostPrev.z, smoothGhostTarget.z)
            );
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // Tick smooth queue every render frame for accurate timing
        if (delayMode.get() == Mode.Smooth) {
            tickSmoothQueue();
        }

        // Determine which ghost to render
        Vec3d renderPos = null;
        double progress = 0;

        if (delayMode.get() == Mode.Smooth) {
            if (smoothQueue.isEmpty() || smoothGhostPos == null) return;
            renderPos = smoothGhostPos;
            // Progress based on how full the queue is
            long now = System.currentTimeMillis();
            if (!smoothQueue.isEmpty()) {
                TimedPacket last = smoothQueue.get(smoothQueue.size() - 1);
                TimedPacket first = smoothQueue.get(0);
                long total = last.releaseAtMs - first.releaseAtMs;
                long elapsed = now - first.releaseAtMs + minDelay.get();
                progress = total > 0
                    ? MathHelper.clamp((double) elapsed / total, 0, 1)
                    : 0;
            }
        } else {
            if (packets.isEmpty() || ghostPos == null) return;
            renderPos = ghostPos;
            progress  = MathHelper.clamp(
                (double)(System.currentTimeMillis() - lastReleaseTime)
                    / currentTargetDelay, 0, 1);
        }

        if (renderPos == null) return;

        Color side = (colorMode.get() == ColorUtils.ColorMode.Fade)
            ? ColorUtils.lerpColor(mainColor.get(), fadeColor.get(), (float) progress)
            : new Color(mainColor.get());
        Color line = (colorMode.get() == ColorUtils.ColorMode.Fade)
            ? ColorUtils.lerpColor(lineColor.get(), lineFadeColor.get(), (float) progress)
            : new Color(lineColor.get());

        if (fadeAlpha.get()) {
            int alphaMult = (int)(255 * (1.0 - progress));
            side.a = (side.a * alphaMult) / 255;
            line.a = (line.a * alphaMult) / 255;
        }

        event.renderer.box(
            renderPos.x - 0.3, renderPos.y, renderPos.z - 0.3,
            renderPos.x + 0.3, renderPos.y + 1.8, renderPos.z + 0.3,
            side, line, ShapeMode.Both, 0);
    }

}