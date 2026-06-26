package com.moid.addon.utils;

import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.util.math.MathHelper;

public class ColorUtils {
    public enum ColorMode { Static, Fade }

    public static Color lerpColor(SettingColor c1, SettingColor c2, float delta) {
        return new Color(
            (int) MathHelper.lerp(delta, c1.r, c2.r),
            (int) MathHelper.lerp(delta, c1.g, c2.g),
            (int) MathHelper.lerp(delta, c1.b, c2.b),
            (int) MathHelper.lerp(delta, c1.a, c2.a)
        );
    }

    public static void applyChroma(Color target, double chromaSpeed, double offset, float saturation, int alpha) {
        double hue = (System.currentTimeMillis() * (chromaSpeed * 0.1) + offset) % 360;
        java.awt.Color javaCol = java.awt.Color.getHSBColor((float) (hue / 360.0), saturation, 1.0f);
        target.set(javaCol.getRed(), javaCol.getGreen(), javaCol.getBlue(), alpha);
    }
}
