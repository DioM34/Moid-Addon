package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.c2s.query.QueryPingC2SPacket;
import net.minecraft.network.packet.c2s.common.CommonPongC2SPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MoidPingSpoof extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode {
        Static,
        Randomize
    }

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .defaultValue(Mode.Static)
        .build()
    );

    // Static Settings
    private final Setting<Integer> staticDelay = sg.add(new IntSetting.Builder()
        .name("static-delay")
        .defaultValue(100)
        .min(0)
        .sliderMax(1000)
        .visible(() -> mode.get() == Mode.Static)
        .build()
    );

    // Randomize Settings
    private final Setting<Integer> minDelay = sg.add(new IntSetting.Builder()
        .name("min-delay")
        .defaultValue(50)
        .min(0)
        .sliderMax(1000)
        .visible(() -> mode.get() == Mode.Randomize)
        .build()
    );

    private final Setting<Integer> maxDelay = sg.add(new IntSetting.Builder()
        .name("max-delay")
        .defaultValue(200)
        .min(0)
        .sliderMax(1000)
        .visible(() -> mode.get() == Mode.Randomize)
        .build()
    );

    private final List<DelayedPacket> packetQueue = new ArrayList<>();

    public MoidPingSpoof() {
        super(AddonTemplate.CATEGORY, "moid-ping-spoof", "Spoofs your latency to the server with static or random delays.");
    }

    @Override
    public void onDeactivate() {
        synchronized (packetQueue) {
            packetQueue.clear();
        }
    }

    @EventHandler
    private void onPacketSend(PacketEvent.Send event) {
        if (mc.getNetworkHandler() == null) return;

        // Only delay ping-related packets
        if (event.packet instanceof KeepAliveC2SPacket || event.packet instanceof QueryPingC2SPacket || event.packet instanceof CommonPongC2SPacket) {
            if (packetQueue.stream().anyMatch(dp -> dp.packet == event.packet)) return;

            int delayTime;
            if (mode.get() == Mode.Static) {
                delayTime = staticDelay.get();
            } else {
                delayTime = ThreadLocalRandom.current().nextInt(minDelay.get(), Math.max(minDelay.get() + 1, maxDelay.get()));
            }

            synchronized (packetQueue) {
                packetQueue.add(new DelayedPacket(event.packet, System.currentTimeMillis() + delayTime));
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
                    mc.getNetworkHandler().sendPacket(dp.packet);
                    return true;
                }
                return false;
            });
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
