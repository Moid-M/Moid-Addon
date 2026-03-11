package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MoidLag extends Module {
    public enum Mode { Static, Random }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgIndicator = settings.createGroup("Indicator");

    private final Setting<Mode> delayMode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("delay-mode").defaultValue(Mode.Static).build());
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder().name("min-delay-ms").defaultValue(200).min(0).sliderMax(1000).build());
    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder().name("max-delay-ms").defaultValue(500).min(0).sliderMax(2000).visible(() -> delayMode.get() == Mode.Random).build());
    private final Setting<Boolean> fullLag = sgGeneral.add(new BoolSetting.Builder().name("full-lag").defaultValue(true).build());
    private final Setting<Boolean> autoRelease = sgGeneral.add(new BoolSetting.Builder().name("auto-release").defaultValue(true).build());
    
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 0, 0, 50)).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 0, 0, 200)).build());

    private final Setting<Boolean> enableHud = sgIndicator.add(new BoolSetting.Builder().name("enable-hud").defaultValue(true).build());
    private final Setting<Double> hudScale = sgIndicator.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(3.0).visible(enableHud::get).build());
    private final Setting<Integer> hudX = sgIndicator.add(new IntSetting.Builder().name("x-offset").defaultValue(0).sliderMin(-500).sliderMax(500).visible(enableHud::get).build());
    private final Setting<Integer> hudY = sgIndicator.add(new IntSetting.Builder().name("y-offset").defaultValue(40).sliderMin(-500).sliderMax(500).visible(enableHud::get).build());
    private final Setting<Boolean> showBar = sgIndicator.add(new BoolSetting.Builder().name("show-bar").defaultValue(true).visible(enableHud::get).build());
    private final Setting<SettingColor> barColor = sgIndicator.add(new ColorSetting.Builder().name("bar-color").defaultValue(new SettingColor(255, 255, 255)).visible(() -> enableHud.get() && showBar.get()).build());
    private final Setting<Boolean> showText = sgIndicator.add(new BoolSetting.Builder().name("show-text").defaultValue(true).visible(enableHud::get).build());

    private final List<Packet<?>> packets = new ArrayList<>();
    private final Random random = new Random();
    private Vec3d ghostPos;
    private long lastReleaseTime;
    private int currentTargetDelay;
    private boolean sending;

    public MoidLag() {
        super(AddonTemplate.CATEGORY, "moid-lag", "Professional packet-choke with fully customizable HUD.");
    }

    @Override
    public void onActivate() {
        resetTimer();
        if (mc.player != null) ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void resetTimer() {
        lastReleaseTime = System.currentTimeMillis();
        int min = minDelay.get();
        int max = maxDelay.get();
        
        if (delayMode.get() == Mode.Static || max <= min) {
            currentTargetDelay = min;
        } else {
            currentTargetDelay = min + random.nextInt(max - min + 1);
        }
    }

    private void releasePackets() {
        if (sending || packets.isEmpty()) return;
        sending = true;
        if (mc.getNetworkHandler() != null) {
            synchronized (packets) {
                packets.forEach(p -> mc.getNetworkHandler().sendPacket(p));
                packets.clear();
            }
        }
        sending = false;
        resetTimer();
        if (mc.player != null) ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (sending || mc.player == null) return;
        Packet<?> p = event.packet;
        if (autoRelease.get() && (p instanceof PlayerInteractEntityC2SPacket || p instanceof PlayerActionC2SPacket || p instanceof PlayerInteractBlockC2SPacket)) {
            releasePackets();
            return;
        }
        if (fullLag.get() || p instanceof PlayerMoveC2SPacket) {
            event.cancel();
            synchronized (packets) { packets.add(p); }
        }
        if (System.currentTimeMillis() - lastReleaseTime >= currentTargetDelay) releasePackets();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!enableHud.get() || packets.isEmpty()) return;

        double baseW = 100, baseH = 5;
        double s = hudScale.get();
        double x = (event.screenWidth / 2.0) - (baseW * s / 2.0) + hudX.get();
        double y = (event.screenHeight / 2.0) + hudY.get();

        long remaining = Math.max(0, currentTargetDelay - (System.currentTimeMillis() - lastReleaseTime));
        double progress = Math.min(1.0, (double) (System.currentTimeMillis() - lastReleaseTime) / currentTargetDelay);

        if (showBar.get()) {
            Renderer2D.COLOR.begin();
            Renderer2D.COLOR.quad(x, y, baseW * s, baseH * s, new Color(20, 20, 20, 150)); // Shadow/BG
            Renderer2D.COLOR.quad(x, y, (baseW * s) * progress, baseH * s, barColor.get());
            Renderer2D.COLOR.render();
        }

        if (showText.get()) {
            String text = remaining + "ms";
            TextRenderer.get().begin(s, false, true);
            double tX = x + (baseW * s / 2.0) - (TextRenderer.get().getWidth(text) / 2.0);
            TextRenderer.get().render(text, tX, y - (TextRenderer.get().getHeight() * s) - 2, barColor.get());
            TextRenderer.get().end();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (ghostPos != null && !packets.isEmpty()) {
            event.renderer.box(ghostPos.x - 0.3, ghostPos.y, ghostPos.z - 0.3, ghostPos.x + 0.3, ghostPos.y + 1.8, ghostPos.z + 0.3, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
        }
    }
}
