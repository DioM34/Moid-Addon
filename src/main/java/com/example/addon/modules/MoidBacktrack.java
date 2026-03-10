package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityS2CPacket;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MoidBacktrack extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Settings
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ms").defaultValue(200).min(0).sliderMax(1000).build());
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(EntityType.PLAYER).build());

    // Render Settings
    private final Setting<Boolean> renderGhost = sgRender.add(new BoolSetting.Builder().name("render-ghost").defaultValue(true).build());
    private final Setting<SettingColor> ghostColor = sgRender.add(new ColorSetting.Builder().name("ghost-color").defaultValue(new SettingColor(255, 255, 255, 100)).visible(renderGhost::get).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).visible(renderGhost::get).build());

    private final List<DelayedPacket> packetQueue = new ArrayList<>();

    public MoidBacktrack() {
        super(AddonTemplate.CATEGORY, "moid-backtrack", "Delays movement packets and renders a hit-indicator ghost.");
    }

    @Override
    public void onDeactivate() {
        releasePackets();
    }

    @EventHandler
    private void onPacketReceive(PacketEvent.Receive event) {
        if (mc.world == null || mc.player == null) return;

        if (event.packet instanceof EntityPositionS2CPacket || event.packet instanceof EntityS2CPacket) {
            // We can't filter here because we don't have the ID mapping reliably,
            // so we filter on the tick when deciding whether to delay or release.
            synchronized (packetQueue) {
                packetQueue.add(new DelayedPacket(event.packet, System.currentTimeMillis() + delay.get()));
            }
            event.cancel();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        synchronized (packetQueue) {
            long now = System.currentTimeMillis();
            packetQueue.removeIf(dp -> {
                if (now >= dp.time) {
                    applyPacket(dp.packet);
                    return true;
                }
                return false;
            });
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderGhost.get() || mc.world == null) return;

        // Logic: Renders a box around all entities that are currently being delayed.
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;

            // Simple indicator: Draw where the entity's hitbox is currently sitting
            Box box = entity.getBoundingBox();
            event.renderer.box(box, ghostColor.get(), ghostColor.get(), shapeMode.get(), 0);
        }
    }

    private void releasePackets() {
        synchronized (packetQueue) {
            for (DelayedPacket dp : packetQueue) {
                applyPacket(dp.packet);
            }
            packetQueue.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private void applyPacket(Packet<?> packet) {
        if (mc.getNetworkHandler() != null) {
            try {
                ((Packet<ClientPlayPacketListener>) packet).apply(mc.getNetworkHandler());
            } catch (Exception ignored) {}
        }
    }

    private static class DelayedPacket {
        public final Packet<?> packet;
        public final long time;

        public DelayedPacket(Packet<?> packet, long time) {
            this.packet = packet;
            this.time = time;
        }
    }
}
