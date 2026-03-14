package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.block.*;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

public class GhostHand extends Module {
    public enum Mode {
        Safe,   // Only interacts if the raymarching finds a block
        Packet  // Force-interacts through anything in range
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCustom = settings.createGroup("Customization");

    // General Settings
    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("How the module bypasses walls.")
        .defaultValue(Mode.Packet)
        .build()
    );

    private final Setting<Keybind> keybind = sgGeneral.add(new KeybindSetting.Builder()
        .name("keybind")
        .description("The key to open containers.")
        .defaultValue(Keybind.fromButton(2))
        .build()
    );

    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder()
        .name("range")
        .defaultValue(5.0)
        .min(0.0)
        .sliderMax(6.0)
        .build()
    );

    // Customization Settings
    private final Setting<Boolean> swing = sgCustom.add(new BoolSetting.Builder()
        .name("swing")
        .description("Swings your hand client-side.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> furnaces = sgCustom.add(new BoolSetting.Builder()
        .name("furnaces")
        .description("Allow opening furnaces/hoppers.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> shulkers = sgCustom.add(new BoolSetting.Builder()
        .name("shulkers")
        .description("Allow opening shulker boxes.")
        .defaultValue(true)
        .build()
    );

    private boolean wasPressed;

    public GhostHand() {
        super(AddonTemplate.CATEGORY, "ghost-hand", "Advanced interaction through walls.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null || mc.currentScreen != null) return;

        boolean isPressed = keybind.get().isPressed();
        if (isPressed && !wasPressed) {
            if (mode.get() == Mode.Packet) {
                findAndOpen();
            } else {
                // Safe mode logic can be tied to standard raycast if preferred
                findAndOpen(); 
            }
        }
        wasPressed = isPressed;
    }

    private void findAndOpen() {
        Vec3d start = mc.player.getEyePos();
        Vec3d direction = mc.player.getRotationVec(1f);
        
        double maxDist = range.get();
        double step = 0.05; // Finer precision for raymarching

        for (double i = 0; i < maxDist; i += step) {
            Vec3d checkPoint = start.add(direction.x * i, direction.y * i, direction.z * i);
            BlockPos pos = BlockPos.ofFloored(checkPoint);
            Block block = mc.world.getBlockState(pos).getBlock();

            if (isValid(block)) {
                openPacket(pos);
                return;
            }
        }
    }

    private void openPacket(BlockPos pos) {
        BlockHitResult bhr = new BlockHitResult(
            new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
            Direction.UP,
            pos,
            false
        );

        // Sending the packet to the server
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
        
        if (swing.get()) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean isValid(Block block) {
        if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof EnderChestBlock) return true;
        if (shulkers.get() && block instanceof ShulkerBoxBlock) return true;
        if (furnaces.get() && (block instanceof AbstractFurnaceBlock || block instanceof HopperBlock)) return true;
        return false;
    }
}