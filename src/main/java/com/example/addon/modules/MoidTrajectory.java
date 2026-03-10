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

public class MoidTrajectory extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> trailLength = sg.add(new IntSetting.Builder().name("trail-length").defaultValue(20).min(1).max(100).build());
    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(5).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 255, 150)).build());

    private final List<Vec3d> points = new ArrayList<>();

    public MoidTrajectory() {
        super(AddonTemplate.CATEGORY, "moid-trajectory", "Renders a momentum-based geometric trail behind you.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        
        // Use direct coordinates to avoid mapping issues
        points.add(new Vec3d(mc.player.getX(), mc.player.getY() + 0.05, mc.player.getZ()));
        
        if (points.size() > trailLength.get()) {
            points.remove(0);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (points.size() < 2) return;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3d p1 = points.get(i);
            Vec3d p2 = points.get(i + 1);

            // Fade the alpha based on age (earlier points are more transparent)
            double percentage = (double) i / points.size();
            int alpha = (int) (color.get().a * percentage);
            SettingColor drawCol = new SettingColor(color.get().r, color.get().g, color.get().b, alpha);

            // Render with manual thickness
            for (int t = 0; t < thickness.get(); t++) {
                double offset = t * 0.005;
                event.renderer.line(p1.x, p1.y + offset, p1.z, p2.x, p2.y + offset, p2.z, drawCol);
            }
        }
    }
}
