package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

public class MoidFly extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode { Vanilla, Packet, Motion, Glide, Zoom }

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Vanilla).build());
    private final Setting<Double> hSpeed = sg.add(new DoubleSetting.Builder().name("horizontal-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Double> vSpeed = sg.add(new DoubleSetting.Builder().name("vertical-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    
    private final Setting<Boolean> longJump = sg.add(new BoolSetting.Builder().name("micro-long-jump").description("Slightly boosts jumps for a 'natural' look.").defaultValue(true).build());
    private final Setting<Double> ljBoost = sg.add(new DoubleSetting.Builder().name("lj-multiplier").defaultValue(1.15).min(1.0).sliderMax(1.5).visible(longJump::get).build());
    
    private final Setting<Boolean> hover = sg.add(new BoolSetting.Builder().name("hover-spoof").description("Tells the server you are on ground.").defaultValue(false).build());
    private final Setting<Boolean> antiKick = sg.add(new BoolSetting.Builder().name("anti-kick").defaultValue(true).build());
    private final Setting<Double> kickDip = sg.add(new DoubleSetting.Builder().name("kick-dip-amount").defaultValue(-0.04).min(-0.2).max(0).visible(antiKick::get).build());

    private final Setting<Double> packetFactor = sg.add(new DoubleSetting.Builder().name("packet-factor").defaultValue(1.0).min(1.0).sliderMax(3.0).visible(() -> mode.get() == Mode.Packet).build());
    private final Setting<Boolean> phase = sg.add(new BoolSetting.Builder().name("phase").defaultValue(false).visible(() -> mode.get() == Mode.Packet).build());

    public MoidFly() {
        super(AddonTemplate.CATEGORY, "moid-fly", "Elite flight suite with corrected strafing.");
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null) {
            mc.player.getAbilities().flying = false;
            mc.player.noClip = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround() || (hover.get() && !mc.options.jumpKey.isPressed() && !mc.options.sneakKey.isPressed());

        switch (mode.get()) {
            case Vanilla -> handleVanilla();
            case Packet -> handlePacket(onGround);
            case Motion -> handleMotion();
            case Glide -> handleGlide();
            case Zoom -> handleZoom();
        }
    }

    private void handleVanilla() {
        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy = vSpeed.get();
        else if (mc.options.sneakKey.isPressed()) vy = -vSpeed.get();
        else if (antiKick.get() && mc.player.age % 20 == 0) vy = kickDip.get();
        applyVelocity(vy);
    }

    private void handlePacket(boolean spoofGround) {
        mc.player.setVelocity(0, 0, 0);
        mc.player.noClip = phase.get();
        Vec3d move = getDirection();
        double y = 0;
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get();
        else if (mc.options.sneakKey.isPressed()) y = -vSpeed.get();

        double loops = packetFactor.get();
        for (int i = 0; i < (int) loops; i++) {
            double nX = mc.player.getX() + (move.x * hSpeed.get() / loops);
            double nY = mc.player.getY() + (y / loops);
            double nZ = mc.player.getZ() + (move.z * hSpeed.get() / loops);
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(nX, nY, nZ, spoofGround, false));
            mc.player.setPosition(nX, nY, nZ);
        }
    }

    private void handleZoom() {
        double curH = (mc.player.age % 4 < 3) ? hSpeed.get() * 1.5 : hSpeed.get() * 0.2;
        double vy = 0;
        if (mc.options.jumpKey.isPressed()) vy = vSpeed.get();
        else if (mc.options.sneakKey.isPressed()) vy = -vSpeed.get();
        
        Vec3d dir = getDirection();
        mc.player.setVelocity(dir.x * curH, vy, dir.z * curH);
    }

    private void handleMotion() {
        double y = 0.001; 
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get() * 0.5;
        else if (mc.options.sneakKey.isPressed()) y = -vSpeed.get() * 0.5;
        applyVelocity(y);
    }

    private void handleGlide() {
        double y = -0.05;
        if (mc.options.jumpKey.isPressed()) y = vSpeed.get() * 0.5;
        else if (mc.options.sneakKey.isPressed()) y = -vSpeed.get();
        applyVelocity(y);
    }

    private void applyVelocity(double y) {
        Vec3d dir = getDirection();
        double x = isMoving() ? dir.x * hSpeed.get() : 0;
        double z = isMoving() ? dir.z * hSpeed.get() : 0;
        
        if (longJump.get() && isMoving() && !mc.player.isOnGround() && mc.player.getVelocity().y > 0) {
            x *= ljBoost.get();
            z *= ljBoost.get();
        }

        mc.player.setVelocity(x, y, z);
    }

    private Vec3d getDirection() {
        // Correcting the Strafe: Swapped the subtract/add logic for side movement
        Vec3d dir = Vec3d.fromPolar(0, mc.player.getYaw()).normalize();
        Vec3d side = dir.rotateY((float) Math.toRadians(90));
        Vec3d out = new Vec3d(0,0,0);
        
        if (mc.options.forwardKey.isPressed()) out = out.add(dir);
        if (mc.options.backKey.isPressed()) out = out.subtract(dir);
        
        // Swapped these: Left now adds the side vector, Right subtracts it
        if (mc.options.leftKey.isPressed()) out = out.add(side);
        if (mc.options.rightKey.isPressed()) out = out.subtract(side);
        
        return out.normalize();
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() || mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }
}
