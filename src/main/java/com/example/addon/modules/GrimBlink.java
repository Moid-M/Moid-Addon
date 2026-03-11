package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class GrimBlink extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> minTicks = sgGeneral.add(new IntSetting.Builder()
        .name("min-ticks")
        .description("Minimum ticks to hold.")
        .defaultValue(10)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<Integer> maxTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-ticks")
        .description("Maximum ticks to hold.")
        .defaultValue(15)
        .min(1)
        .sliderMax(20)
        .build()
    );

    private final Setting<SettingColor> boxColor = sgRender.add(new ColorSetting.Builder()
        .name("ghost-box-color")
        .description("The color of your server-side ghost.")
        .defaultValue(new SettingColor(255, 255, 255, 100))
        .build()
    );

    private final CopyOnWriteArrayList<Packet<?>> packetQueue = new CopyOnWriteArrayList<>();
    private Vec3d serverPos;
    private int timer = 0;
    private int currentTarget = 15;

    public GrimBlink() {
        super(AddonTemplate.CATEGORY, "grim-blink", "Buffers movement with a 3D ghost indicator.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            serverPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
        currentTarget = ThreadLocalRandom.current().nextInt(minTicks.get(), Math.max(minTicks.get(), maxTicks.get()) + 1);
    }

    @Override
    public void onDeactivate() {
        flush();
        timer = 0;
        serverPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        
        timer++;
        if (timer >= currentTarget) {
            flush();
            serverPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            timer = 0;
            currentTarget = ThreadLocalRandom.current().nextInt(minTicks.get(), Math.max(minTicks.get(), maxTicks.get()) + 1);
        }
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (event.packet instanceof PlayerMoveC2SPacket) {
            packetQueue.add(event.packet);
            event.cancel();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (serverPos == null) return;
        event.renderer.box(serverPos.x - 0.3, serverPos.y, serverPos.z - 0.3, 
                           serverPos.x + 0.3, serverPos.y + 1.8, serverPos.z + 0.3, 
                           boxColor.get(), boxColor.get(), ShapeMode.Both, 0);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null) return;
        String text = "Blink: " + timer + " / " + currentTarget;
        float progress = (float) timer / currentTarget;
        Color dynamicColor = new Color(255, (int) (255 * (1 - progress)), (int) (255 * (1 - progress)));
        TextRenderer.get().begin(1.2, false, false);
        double w = TextRenderer.get().getWidth(text);
        TextRenderer.get().render(text, (event.screenWidth / 2.0) - (w / 2), (event.screenHeight / 2.0) + 20, dynamicColor);
        TextRenderer.get().end();
    }

    private void flush() {
        if (mc.getNetworkHandler() != null && !packetQueue.isEmpty()) {
            for (Packet<?> p : packetQueue) {
                mc.getNetworkHandler().sendPacket(p);
            }
        }
        packetQueue.clear();
    }
}
