package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MoidHUD extends Module {
    public enum SortMode { Longest, Shortest, Alphabetical, AlphabeticalReverse }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgArray = settings.createGroup("ArrayList");
    private final SettingGroup sgInfo = settings.createGroup("Info Visibility");
    private final SettingGroup sgColors = settings.createGroup("Colors");

    // --- Layout ---
    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(2.0).build());
    private final Setting<Integer> xPos = sg.add(new IntSetting.Builder().name("x-offset").defaultValue(10).min(0).sliderMax(4000).max(8000).build());
    private final Setting<Integer> yPos = sg.add(new IntSetting.Builder().name("y-offset").defaultValue(10).min(0).sliderMax(2000).max(4000).build());
    private final Setting<Boolean> mirror = sg.add(new BoolSetting.Builder().name("mirror").defaultValue(false).build());

    // --- Colors ---
    private final Setting<Boolean> chroma = sgColors.add(new BoolSetting.Builder().name("chroma").defaultValue(false).build());
    private final Setting<Double> chromaSpeed = sgColors.add(new DoubleSetting.Builder().name("chroma-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(chroma::get).build());
    
    private final Setting<SettingColor> colorStart = sgColors.add(new ColorSetting.Builder().name("color-start").defaultValue(new SettingColor(255, 255, 255)).visible(() -> !chroma.get()).build());
    private final Setting<Boolean> useSecondColor = sgColors.add(new BoolSetting.Builder().name("use-second-color").defaultValue(false).visible(() -> !chroma.get()).build());
    private final Setting<SettingColor> colorEnd = sgColors.add(new ColorSetting.Builder().name("color-end").defaultValue(new SettingColor(150, 150, 150)).visible(() -> !chroma.get() && useSecondColor.get()).build());
    private final Setting<Double> gradientSpeed = sgColors.add(new DoubleSetting.Builder().name("gradient-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).visible(() -> !chroma.get() && useSecondColor.get()).build());

    // --- Info Visibility ---
    private final Setting<Boolean> showClient = sgInfo.add(new BoolSetting.Builder().name("show-client-info").defaultValue(true).build());
    private final Setting<Boolean> showStats = sgInfo.add(new BoolSetting.Builder().name("show-stats").defaultValue(true).build());
    private final Setting<Boolean> showPos = sgInfo.add(new BoolSetting.Builder().name("show-coords").defaultValue(true).build());
    private final Setting<Boolean> showWorld = sgInfo.add(new BoolSetting.Builder().name("show-world").defaultValue(true).build());
    private final Setting<Boolean> showTime = sgInfo.add(new BoolSetting.Builder().name("show-date-time").defaultValue(true).build());

    // --- ArrayList ---
    private final Setting<Boolean> showArrayList = sgArray.add(new BoolSetting.Builder().name("show-active-modules").defaultValue(true).build());
    private final Setting<SortMode> sortMode = sgArray.add(new EnumSetting.Builder<SortMode>().name("sort-mode").defaultValue(SortMode.Longest).visible(showArrayList::get).build());

    private String timeString = "";
    private int tickCounter = 0;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Color workingColor = new Color();

    public MoidHUD() {
        super(AddonTemplate.CATEGORY, "moid-hud", "Advanced HUD with Dual-Color gradients and Chroma.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden) return;
        if (tickCounter++ % 20 == 0) timeString = dtf.format(LocalTime.now());

        float x = xPos.get();
        float y = yPos.get();
        double s = scale.get();
        
        TextRenderer.get().begin(s, false, true);
        float lineHeight = (float) (TextRenderer.get().getHeight() + 2);

        if (showClient.get()) {
            renderLine("Meteor Client | Moid Addon | 1.21.1", x, y, s);
            y += lineHeight * s;
        }

        if (showStats.get()) {
            int fps = MinecraftClient.getInstance().getCurrentFps();
            int ping = (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0;
            renderLine("FPS: " + fps + " | Ping: " + ping + "ms | HP: " + String.format("%.1f", mc.player.getHealth()), x, y, s);
            y += lineHeight * s;
        }

        if (showPos.get()) {
            renderLine(String.format("XYZ: %.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()), x, y, s);
            y += lineHeight * s;
        }
        
        if (showWorld.get()) {
            String dir = "Facing: " + mc.player.getHorizontalFacing().asString().toUpperCase();
            String biome = "Biome: " + mc.world.getBiome(mc.player.getBlockPos()).getKey().get().getValue().getPath().replace("_", " ");
            renderLine(dir + " (" + biome + ")", x, y, s);
            y += lineHeight * s;
        }

        if (showTime.get()) {
            renderLine("Time: " + timeString, x, y, s);
            y += lineHeight * s;
        }

        if (showArrayList.get()) {
            y += 4 * s;
            List<Module> active = new ArrayList<>(Modules.get().getActive());
            active.sort((m1, m2) -> {
                switch (sortMode.get()) {
                    case Longest: return Double.compare(TextRenderer.get().getWidth(m2.title), TextRenderer.get().getWidth(m1.title));
                    case Shortest: return Double.compare(TextRenderer.get().getWidth(m1.title), TextRenderer.get().getWidth(m2.title));
                    case Alphabetical: return m1.title.compareTo(m2.title);
                    case AlphabeticalReverse: return m2.title.compareTo(m1.title);
                    default: return 0;
                }
            });

            for (Module m : active) {
                renderLine("> " + m.title, x, y, s);
                y += lineHeight * s;
            }
            renderLine("Total Modules: " + active.size(), x, y, s);
        }

        TextRenderer.get().end();
    }

    private void renderLine(String text, float x, float y, double s) {
        float drawX = x;
        if (mirror.get()) drawX -= (float) (TextRenderer.get().getWidth(text) * s);

        if (chroma.get()) {
            double hue = (System.currentTimeMillis() * (chromaSpeed.get() * 0.1) + (y * 2)) % 360;
            java.awt.Color c = java.awt.Color.getHSBColor((float) (hue / 360f), 0.7f, 1f);
            workingColor.set(c.getRed(), c.getGreen(), c.getBlue(), colorStart.get().a);
        } 
        else if (useSecondColor.get()) {
            // Gradient Rolling Logic
            double wave = (Math.sin((System.currentTimeMillis() * 0.001 * gradientSpeed.get()) + (y * 0.01)) + 1.0) / 2.0;
            SettingColor sC = colorStart.get();
            SettingColor eC = colorEnd.get();
            workingColor.set(
                (int) (eC.r + (sC.r - eC.r) * wave),
                (int) (eC.g + (sC.g - eC.g) * wave),
                (int) (eC.b + (sC.b - eC.b) * wave),
                sC.a
            );
        } 
        else {
            workingColor.set(colorStart.get());
        }
        
        TextRenderer.get().render(text, drawX / (float) s, y / (float) s, workingColor, true);
    }
}