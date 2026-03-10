package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.MathHelper;
import java.util.Set;

public class MoidCircleESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(3.0).min(0).sliderMax(6).build());
    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(10).build());
    private final Setting<Integer> fadeIntensity = sg.add(new IntSetting.Builder().name("fade-intensity").defaultValue(5).min(0).max(20).build());
    private final Setting<Double> fadeDistance = sg.add(new DoubleSetting.Builder().name("fade-distance").defaultValue(0.3).min(0).sliderMax(1).build());

    private final Setting<SettingColor> baseColor = sg.add(new ColorSetting.Builder().name("base-color").defaultValue(new SettingColor(255, 255, 255)).build());
    private final Setting<SettingColor> targetColor = sg.add(new ColorSetting.Builder().name("target-color").defaultValue(new SettingColor(255, 0, 0)).build());
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());

    public MoidCircleESP() {
        super(AddonTemplate.CATEGORY, "moid-circle-esp", "Lag-free smooth reach circle with multi-layer fade.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        boolean targetInRange = false;
        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;
            if (mc.player.distanceTo(entity) <= range.get()) {
                targetInRange = true;
                break;
            }
        }

        SettingColor color = targetInRange ? targetColor.get() : baseColor.get();

        double x = MathHelper.lerp(event.tickDelta, mc.player.lastRenderX, mc.player.getX());
        double y = MathHelper.lerp(event.tickDelta, mc.player.lastRenderY, mc.player.getY()) + 0.05;
        double z = MathHelper.lerp(event.tickDelta, mc.player.lastRenderZ, mc.player.getZ());

        double radius = range.get();
        int segments = 90; // Balanced for performance and smoothness
        double increment = 360.0 / segments;

        for (int i = 0; i < segments; i++) {
            double ang1 = Math.toRadians(i * increment);
            double ang2 = Math.toRadians((i + 1) * increment);

            double cos1 = Math.cos(ang1);
            double sin1 = Math.sin(ang1);
            double cos2 = Math.cos(ang2);
            double sin2 = Math.sin(ang2);

            // 1. Draw Main Thick Lines
            for (int t = 0; t < thickness.get(); t++) {
                event.renderer.line(x + cos1 * radius, y + (t * 0.002), z + sin1 * radius, 
                                    x + cos2 * radius, y + (t * 0.002), z + sin2 * radius, color);
            }

            // 2. Draw Multi-Layer Fade (Bulletproof method)
            if (fadeIntensity.get() > 0 && fadeDistance.get() > 0) {
                for (int f = 1; f <= fadeIntensity.get(); f++) {
                    double layerRadius = radius + (f * (fadeDistance.get() / fadeIntensity.get()));
                    // Calculate alpha drop: further out = more transparent
                    int alpha = Math.max(0, color.a - (f * (color.a / fadeIntensity.get())));
                    SettingColor fadeCol = new SettingColor(color.r, color.g, color.b, alpha);
                    
                    event.renderer.line(x + cos1 * layerRadius, y, z + sin1 * layerRadius, 
                                        x + cos2 * layerRadius, y, z + sin2 * layerRadius, fadeCol);
                }
            }
        }
    }
}
