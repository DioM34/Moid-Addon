package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MoidHUD extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder().name("scale").defaultValue(1.0).min(0.5).sliderMax(2.0).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("text-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<Integer> xPos = sg.add(new IntSetting.Builder().name("x-offset").defaultValue(10).sliderMax(1000).build());
    private final Setting<Integer> yPos = sg.add(new IntSetting.Builder().name("y-offset").defaultValue(10).sliderMax(1000).build());

    private final Setting<Boolean> showClient = sg.add(new BoolSetting.Builder().name("show-client-info").defaultValue(true).build());
    private final Setting<Boolean> showStats = sg.add(new BoolSetting.Builder().name("show-stats").defaultValue(true).build());
    private final Setting<Boolean> showPos = sg.add(new BoolSetting.Builder().name("show-coords").defaultValue(true).build());
    private final Setting<Boolean> showWorld = sg.add(new BoolSetting.Builder().name("show-world").defaultValue(true).build());
    private final Setting<Boolean> showTime = sg.add(new BoolSetting.Builder().name("show-date-time").defaultValue(true).build());
    private final Setting<Boolean> showArrayList = sg.add(new BoolSetting.Builder().name("show-active-modules").defaultValue(true).build());

    public MoidHUD() {
        super(AddonTemplate.CATEGORY, "moid-hud", "Customizable international HUD for Moid Addon.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden) return;

        float x = xPos.get();
        float y = yPos.get();
        double s = scale.get();

        // 1. Client Info
        if (showClient.get()) {
            y += draw("Meteor Client | Moid Addon | 1.21.1", x, y, s);
        }

        // 2. Stats
        if (showStats.get()) {
            int fps = MinecraftClient.getInstance().getCurrentFps();
            int ping = (mc.getNetworkHandler() != null && mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()) != null) ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid()).getLatency() : 0;
            y += draw(String.format("FPS: %d | Ping: %dms | HP: %.1f", fps, ping, mc.player.getHealth()), x, y, s);
        }

        // 3. Position & Direction
        if (showPos.get()) {
            y += draw(String.format("XYZ: %.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()), x, y, s);
        }
        
        if (showWorld.get()) {
            String dir = "Facing: " + mc.player.getHorizontalFacing().asString().toUpperCase();
            String biome = "Biome: " + mc.world.getBiome(mc.player.getBlockPos()).getKey().get().getValue().getPath();
            y += draw(dir + " (" + biome + ")", x, y, s);
        }

        // 4. Time
        if (showTime.get()) {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
            y += draw("Time: " + dtf.format(LocalDateTime.now()), x, y, s);
        }

        // 5. ArrayList
        if (showArrayList.get()) {
            y += 4 * s;
            List<Module> active = Modules.get().getActive();
            for (Module m : active) {
                y += draw("> " + m.title, x, y, s);
            }
            y += draw("Total Modules: " + active.size(), x, y, s);
        }
    }

    private float draw(String text, float x, float y, double s) {
        TextRenderer.get().begin(s, false, true);
        float height = (float) TextRenderer.get().getHeight();
        TextRenderer.get().render(text, x / (float) s, y / (float) s, color.get(), true);
        TextRenderer.get().end();
        return (height + 2) * (float) s;
    }
}
