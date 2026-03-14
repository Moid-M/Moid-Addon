package com.example.addon;

import com.example.addon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class AddonTemplate extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Moid");

    @Override
    public void onInitialize() {
        Modules m = Modules.get();
        // Movement
        m.add(new MoidFly());
        m.add(new VulcanWeb());
                
        // Visuals & HUD
        m.add(new MoidHUD());
        m.add(new MoidCircleESP());
        m.add(new MoidGhostESP());
        m.add(new MoidJumpCircle());
        m.add(new JumpMatrix());
        m.add(new MoidTrails());
        m.add(new MoidHitESP());
        m.add(new MoidHitParticles());
        m.add(new ProjectilePredictor());
        m.add(new TargetHUD());

        // Combat & Utility
        m.add(new MoidLag());
        m.add(new MoidKillAura());
        m.add(new MoidAutoClicker());
        m.add(new ACDetector());
        m.add(new GrimBlink());
        m.add(new Triggerbot());
        m.add(new AntiResourcePack());
        m.add(new GhostHand());
        m.add(new MoidAim());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.example.addon";
    }
}
