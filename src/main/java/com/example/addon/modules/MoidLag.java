package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MoidLag extends Module {
    public enum Mode { Static, Random }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgHud = settings.createGroup("HUD Layout");
    private final SettingGroup sgColors = settings.createGroup("HUD Colors");

    private final Setting<Mode> delayMode = sgGeneral.add(new EnumSetting.Builder<Mode>().name("delay-mode").defaultValue(Mode.Static).build());
    private final Setting<Integer> minDelay = sgGeneral.add(new IntSetting.Builder().name("min-delay-ms").defaultValue(200).min(0).sliderMax(1000).build());
    private final Setting<Integer> maxDelay = sgGeneral.add(new IntSetting.Builder().name("max-delay-ms").defaultValue(500).min(0).sliderMax(2000).visible(() -> delayMode.get() == Mode.Random).build());
    private final Setting<Boolean> autoRelease = sgGeneral.add(new BoolSetting.Builder().name("auto-release").defaultValue(true).build());
    
    private final Setting<Double> hudScale = sgHud.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(3.0).build());
    private final Setting<Integer> hudX = sgHud.add(new IntSetting.Builder().name("x-offset").defaultValue(0).sliderMin(-1000).sliderMax(1000).build());
    private final Setting<Integer> hudY = sgHud.add(new IntSetting.Builder().name("y-offset").defaultValue(60).sliderMin(-1000).sliderMax(1000).build());
    
    private final Setting<SettingColor> textColor = sgColors.add(new ColorSetting.Builder().name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> bgColor = sgColors.add(new ColorSetting.Builder().name("background-color").defaultValue(new SettingColor(0, 0, 0, 150)).build());
    private final Setting<SettingColor> barColor = sgColors.add(new ColorSetting.Builder().name("bar-color").defaultValue(new SettingColor(120, 120, 255)).build());

    private final List<Packet<?>> packets = new CopyOnWriteArrayList<>();
    private final Random random = new Random();
    private Vec3d ghostPos;
    private long lastReleaseTime;
    private int currentTargetDelay;
    private boolean isReleasing;

    public MoidLag() {
        super(AddonTemplate.CATEGORY, "moid-lag", "Universal rendering version.");
    }

    @Override
    public void onActivate() {
        resetTimer();
        packets.clear();
        if (mc.player != null) ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    private void resetTimer() {
        lastReleaseTime = System.currentTimeMillis();
        int min = minDelay.get();
        int max = maxDelay.get();
        currentTargetDelay = (delayMode.get() == Mode.Static || max <= min) ? min : min + random.nextInt(max - min + 1);
    }

    private void releasePackets() {
        if (packets.isEmpty() || mc.getNetworkHandler() == null) return;
        isReleasing = true;
        try {
            for (Packet<?> p : packets) {
                mc.getNetworkHandler().sendPacket(p);
            }
        } catch (Exception ignored) {}
        packets.clear();
        isReleasing = false;
        resetTimer();
        if (mc.player != null) ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (mc.player == null || isReleasing) return;
        Packet<?> p = event.packet;
        if (autoRelease.get() && (p instanceof PlayerInteractEntityC2SPacket || p instanceof PlayerActionC2SPacket || p instanceof PlayerInteractBlockC2SPacket)) {
            releasePackets();
            return;
        }
        if (p instanceof PlayerMoveC2SPacket || p instanceof PlayerInputC2SPacket) {
            event.cancel();
            packets.add(p);
        }
        if (System.currentTimeMillis() - lastReleaseTime >= currentTargetDelay) releasePackets();
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (packets.isEmpty()) return;

        double s = hudScale.get();
        long remaining = Math.max(0, currentTargetDelay - (System.currentTimeMillis() - lastReleaseTime));
        String text = remaining + "ms (" + packets.size() + "p)";
        
        int padding = (int) (6 * s);
        int textW = (int) (TextRenderer.get().getWidth(text) * s);
        int textH = (int) (TextRenderer.get().getHeight() * s);
        int w = textW + (padding * 2);
        int h = textH + (int) (padding * 1.5);
        
        int x = (int) ((event.screenWidth / 2.0) - (w / 2.0) + hudX.get());
        int y = (int) ((event.screenHeight / 2.0) + hudY.get());

        // Using getPacked() to support older/different Meteor API versions
        event.drawContext.fill(x, y, x + w, y + h, bgColor.get().getPacked());
        
        double progress = Math.min(1.0, (double) (System.currentTimeMillis() - lastReleaseTime) / currentTargetDelay);
        int barWidth = (int) (w * progress);
        int barHeight = (int) (2 * s);
        event.drawContext.fill(x, y + h - barHeight, x + barWidth, y + h, barColor.get().getPacked());

        TextRenderer.get().begin(s, false, true);
        TextRenderer.get().render(text, x + padding, y + (padding / 2.0), textColor.get());
        TextRenderer.get().end();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (ghostPos != null && !packets.isEmpty()) {
            event.renderer.box(ghostPos.x - 0.3, ghostPos.y, ghostPos.z - 0.3, ghostPos.x + 0.3, ghostPos.y + 1.8, ghostPos.z + 0.3, new Color(255, 255, 255, 25), barColor.get(), ShapeMode.Both, 0);
        }
    }
}