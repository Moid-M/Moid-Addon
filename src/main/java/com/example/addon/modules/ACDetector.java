package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.util.Formatting;
import java.util.ArrayList;
import java.util.List;

public class ACDetector extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCommands = settings.createGroup("Command Scanner");

    private final Setting<Integer> scanDelay = sgGeneral.add(new IntSetting.Builder()
        .name("scan-delay")
        .description("Ticks between each command (20 ticks = 1 second).")
        .defaultValue(10)
        .min(1)
        .build()
    );

    private final String[] acNames = {"grim", "vulcan", "spartan", "ncp", "matrix", "karhu", "verus", "intave"};
    private final List<Setting<Boolean>> commandToggles = new ArrayList<>();
    
    private final List<String> queue = new ArrayList<>();
    private int timer = 0;

    public ACDetector() {
        super(AddonTemplate.CATEGORY, "ac-detector", "Strictly identifies Anticheats by intercepting responses.");
        for (String ac : acNames) {
            commandToggles.add(sgCommands.add(new BoolSetting.Builder().name("scan-" + ac).defaultValue(true).build()));
        }
    }

    @Override
    public void onActivate() {
        queue.clear();
        timer = 0;
        for (int i = 0; i < acNames.length; i++) {
            if (commandToggles.get(i).get()) queue.add(acNames[i]);
        }
        if (!queue.isEmpty()) info(Formatting.GRAY + "Starting strict scan...");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (queue.isEmpty() || mc.getNetworkHandler() == null) return;

        timer++;
        if (timer >= scanDelay.get()) {
            String cmd = queue.remove(0);
            mc.getNetworkHandler().sendChatCommand(cmd);
            timer = 0;
        }
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (event.packet instanceof GameMessageS2CPacket p) {
            String content = p.content().getString().toLowerCase();

            // Ignore common false positives
            if (content.contains("unknown command") || content.contains("usage:") || content.contains("type /help")) {
                event.cancel();
                return;
            }

            for (String ac : acNames) {
                // If the message contains the name AND a version indicator, it's a match
                if (content.contains(ac) && (content.contains("version") || content.contains("anticheat") || content.contains("author") || content.contains("developed"))) {
                    event.cancel();
                    info(Formatting.GREEN + "Confirmed: " + Formatting.WHITE + ac.toUpperCase());
                    queue.remove(ac);
                    return;
                }
            }
        }
    }
}
