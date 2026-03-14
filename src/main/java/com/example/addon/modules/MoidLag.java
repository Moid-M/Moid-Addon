package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MoidLag extends Module {
    public enum Mode { Static, Random }
    public enum ColorMode { Static, Fade }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // General Settings
    private final Setting<Mode> delayMode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("delay-mode").defaultValue(Mode.Static).build());
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder().name("min-delay-ms").defaultValue(200).min(0).sliderMax(1000).build());
    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder().name("max-delay-ms").defaultValue(500).min(0).sliderMax(2000).visible(() -> delayMode.get() == Mode.Random).build());
    private final Setting<Boolean> fullLag = sgGeneral.add(new BoolSetting.Builder().name("full-lag").defaultValue(false).build());
    private final Setting<Boolean> autoRelease = sgGeneral.add(new BoolSetting.Builder().name("auto-release").defaultValue(true).build());
    
    // Render Settings
    private final Setting<ColorMode> colorMode = sgRender.add(new EnumSetting.Builder<ColorMode>().name("color-mode").defaultValue(ColorMode.Fade).build());
    private final Setting<Boolean> fadeAlpha = sgRender.add(new BoolSetting.Builder().name("fade-alpha").defaultValue(true).build());
    
    private final Setting<SettingColor> mainColor = sgRender.add(new ColorSetting.Builder().name("main-color").defaultValue(new SettingColor(120, 120, 255, 100)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(120, 120, 255, 200)).build());
    
    private final Setting<SettingColor> fadeColor = sgRender.add(new ColorSetting.Builder().name("fade-to-color").defaultValue(new SettingColor(255, 50, 50, 100)).visible(() -> colorMode.get() == ColorMode.Fade).build());
    private final Setting<SettingColor> lineFadeColor = sgRender.add(new ColorSetting.Builder().name("line-fade-to").defaultValue(new SettingColor(255, 50, 50, 200)).visible(() -> colorMode.get() == ColorMode.Fade).build());

    private final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private Vec3d ghostPos;
    private long lastReleaseTime;
    private int currentTargetDelay;
    private boolean isReleasing;

    public MoidLag() {
        super(AddonTemplate.CATEGORY, "moid-lag", "Clean FakeLag with optimized box rendering for 1.21.4.");
    }

    @Override
    public void onDeactivate() {
        packets.clear();
        ghostPos = null;
    }

    private void resetTimer() {
        lastReleaseTime = System.currentTimeMillis();
        int min = minDelay.get();
        int max = maxDelay.get();
        currentTargetDelay = (delayMode.get() == Mode.Static || max <= min) ? min : min + random.nextInt(max - min + 1);
    }

    private void captureGhost() {
        if (mc.player == null) return;
        ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
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

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || isReleasing) return;
        Packet<?> p = event.packet;
        if (p instanceof KeepAliveC2SPacket) return;

        if (autoRelease.get() && (p instanceof PlayerInteractEntityC2SPacket || p instanceof PlayerActionC2SPacket || p instanceof PlayerInteractBlockC2SPacket)) {
            releasePackets();
            return;
        }

        if (fullLag.get() || p instanceof PlayerMoveC2SPacket || p instanceof PlayerInputC2SPacket) {
            if (packets.isEmpty()) captureGhost();
            event.cancel();
            packets.add(p);
        }

        if (System.currentTimeMillis() - lastReleaseTime >= currentTargetDelay) releasePackets();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (packets.isEmpty() || ghostPos == null) return;

        double progress = MathHelper.clamp((double) (System.currentTimeMillis() - lastReleaseTime) / currentTargetDelay, 0, 1);
        
        // Color Selection
        Color side = (colorMode.get() == ColorMode.Fade) ? lerpColor(mainColor.get(), fadeColor.get(), (float) progress) : new Color(mainColor.get());
        Color line = (colorMode.get() == ColorMode.Fade) ? lerpColor(lineColor.get(), lineFadeColor.get(), (float) progress) : new Color(lineColor.get());

        // Alpha Fading
        if (fadeAlpha.get()) {
            int alphaMult = (int) (255 * (1.0 - progress));
            side.a = (side.a * alphaMult) / 255;
            line.a = (line.a * alphaMult) / 255;
        }

        // Render the Indicator Box
        event.renderer.box(ghostPos.x - 0.3, ghostPos.y, ghostPos.z - 0.3, ghostPos.x + 0.3, ghostPos.y + 1.8, ghostPos.z + 0.3, side, line, ShapeMode.Both, 0);
    }

    private Color lerpColor(SettingColor c1, SettingColor c2, float delta) {
        return new Color(
            (int) MathHelper.lerp(delta, c1.r, c2.r),
            (int) MathHelper.lerp(delta, c1.g, c2.g),
            (int) MathHelper.lerp(delta, c1.b, c2.b),
            (int) MathHelper.lerp(delta, c1.a, c2.a)
        );
    }
}