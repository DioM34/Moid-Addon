package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.List;

public class MoidTrails extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> trailLength = sg.add(new IntSetting.Builder().name("trail-length").defaultValue(40).min(1).max(200).build());
    private final Setting<Double> thickness = sg.add(new DoubleSetting.Builder().name("thickness").defaultValue(0.08).min(0.01).sliderMax(0.5).build());
    private final Setting<Double> fadeSpeed = sg.add(new DoubleSetting.Builder().name("fade-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 255, 200)).build());

    private final List<TrailPoint> points = new ArrayList<>();

    public MoidTrails() {
        super(AddonTemplate.CATEGORY, "moid-trails", "A heavy geometric trail with custom fade animations.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        
        // Add current position
        points.add(new TrailPoint(new Vec3d(mc.player.getX(), mc.player.getY() + 0.1, mc.player.getZ())));
        
        // Remove points that exceed length or age
        if (points.size() > trailLength.get()) {
            points.remove(0);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (points.size() < 2) return;

        for (int i = 0; i < points.size() - 1; i++) {
            TrailPoint p1 = points.get(i);
            TrailPoint p2 = points.get(i + 1);

            // Calculate fade based on position in list and fadeSpeed
            double ageFactor = (double) i / points.size();
            int alpha = (int) (color.get().a * Math.pow(ageFactor, fadeSpeed.get()));
            
            SettingColor drawCol = new SettingColor(color.get().r, color.get().g, color.get().b, alpha);

            // Render a "thick" ribbon by drawing multiple offset lines
            double t = thickness.get();
            event.renderer.line(p1.pos.x, p1.pos.y, p1.pos.z, p2.pos.x, p2.pos.y, p2.pos.z, drawCol);
            event.renderer.line(p1.pos.x, p1.pos.y + t, p1.pos.z, p2.pos.x, p2.pos.y + t, p2.pos.z, drawCol);
            event.renderer.line(p1.pos.x - t, p1.pos.y + (t/2), p1.pos.z - t, p2.pos.x - t, p2.pos.y + (t/2), p2.pos.z - t, drawCol);
            event.renderer.line(p1.pos.x + t, p1.pos.y + (t/2), p1.pos.z + t, p2.pos.x + t, p2.pos.y + (t/2), p2.pos.z + t, drawCol);
        }
    }

    private static class TrailPoint {
        public final Vec3d pos;
        public TrailPoint(Vec3d pos) {
            this.pos = pos;
        }
    }
}
