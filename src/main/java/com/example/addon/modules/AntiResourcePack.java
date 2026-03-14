package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

public class AntiResourcePack extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> sendResponse = sgGeneral.add(new BoolSetting.Builder()
        .name("send-fake-response")
        .description("Tells the server you successfully loaded the pack.")
        .defaultValue(true)
        .build()
    );

    public AntiResourcePack() {
        super(AddonTemplate.CATEGORY, "anti-resource-pack", "Prevents servers from forcing resource packs on you.");
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof ResourcePackSendS2CPacket) {
            // Cancel the packet so the download prompt never shows up
            event.cancel();

            // If the server requires a response to let you play, we send "SUCCESSFULLY_LOADED"
            if (sendResponse.get() && mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(
                    ((ResourcePackSendS2CPacket) event.packet).id(),
                    ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED
                ));
                
                info("Bypassed resource pack from server.");
            }
        }
    }
}