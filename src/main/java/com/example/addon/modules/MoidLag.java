package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.renderer.Renderer2D;
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

public class MoidLag extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Boolean> fullLag = sgGeneral.add(new BoolSetting.Builder().name("full-lag").defaultValue(true).build());
    private final Setting<Boolean> autoRelease = sgGeneral.add(new BoolSetting.Builder().name("auto-release").defaultValue(true).build());
    private final Setting<Integer> packetLimit = sgGeneral.add(new IntSetting.Builder().name("packet-limit").defaultValue(100).sliderMax(500).build());
    private final Setting<Boolean> showBar = sgRender.add(new BoolSetting.Builder().name("show-hud-bar").defaultValue(true).build());
    private final Setting<SettingColor> ghostColor = sgRender.add(new ColorSetting.Builder().name("ghost-color").defaultValue(new SettingColor(255, 0, 0, 100)).build());

    private final List<Packet<?>> packets = new ArrayList<>();
    private Vec3d ghostPos;
    private boolean sending;

    public MoidLag() {
        super(AddonTemplate.CATEGORY, "moid-lag", "Professional packet-choke with HUD indicators.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            ghostPos = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        }
    }

    @Override
    public void onDeactivate() {
        if (sending) return;
        sending = true;
        synchronized (packets) {
            if (mc.player != null && mc.player.networkHandler != null) {
                packets.forEach(p -> mc.player.networkHandler.sendPacket(p));
            }
            packets.clear();
        }
        sending = false;
    }

    @EventHandler
    private void onSendPacket(PacketEvent.Send event) {
        if (sending || mc.player == null) return;
        Packet<?> p = event.packet;

        if (autoRelease.get() && (p instanceof PlayerInteractEntityC2SPacket || p instanceof PlayerActionC2SPacket || p instanceof PlayerInteractBlockC2SPacket)) {
            onDeactivate();
            onActivate();
            return;
        }

        if (fullLag.get() || p instanceof PlayerMoveC2SPacket) {
            event.cancel();
            synchronized (packets) { packets.add(p); }
        }

        if (packets.size() >= packetLimit.get()) {
            onDeactivate();
            onActivate();
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!showBar.get() || packets.isEmpty()) return;

        double width = 100;
        double height = 6;
        double x = (event.screenWidth / 2.0) - (width / 2.0);
        double y = (event.screenHeight / 2.0) + 25;

        double progress = Math.min(1.0, (double) packets.size() / packetLimit.get());
        Color barColor = new Color((int) (255 * progress), (int) (255 * (1 - progress)), 0, 255);

        Renderer2D.COLOR.begin();
        Renderer2D.COLOR.quad(x, y, width * progress, height, barColor);
        Renderer2D.COLOR.render();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (ghostPos != null && !packets.isEmpty()) {
            double x = ghostPos.x;
            double y = ghostPos.y;
            double z = ghostPos.z;
            event.renderer.box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3, ghostColor.get(), ghostColor.get(), ShapeMode.Both, 0);
        }
    }
}
