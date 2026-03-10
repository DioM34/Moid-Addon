package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

public class MoidTargetFocus extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder().name("bracket-size").defaultValue(0.2).min(0.05).sliderMax(0.5).build());
    private final Setting<Double> spinSpeed = sg.add(new DoubleSetting.Builder().name("spin-speed").defaultValue(3.0).min(0).sliderMax(10).build());
    private final Setting<Boolean> healthSync = sg.add(new BoolSetting.Builder().name("health-sync").description("Brackets shrink as target loses health.").defaultValue(true).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 150, 255)).build());

    public MoidTargetFocus() {
        super(AddonTemplate.CATEGORY, "moid-target-focus", "Tactical HUD brackets that lock onto your combat target.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        KillAura aura = Modules.get().get(KillAura.class);
        if (aura == null || !aura.isActive() || !(aura.getTarget() instanceof LivingEntity target)) return;

        double x = MathHelper.lerp(event.tickDelta, target.lastRenderX, target.getX());
        double y = MathHelper.lerp(event.tickDelta, target.lastRenderY, target.getY());
        double z = MathHelper.lerp(event.tickDelta, target.lastRenderZ, target.getZ());
        
        Box b = target.getBoundingBox();
        double h = b.maxY - b.minY;
        
        // Calculate health ratio (0.0 to 1.0)
        double healthRatio = MathHelper.clamp(target.getHealth() / target.getMaxHealth(), 0, 1);
        
        // Brackets get tighter as health drops if healthSync is on
        double currentSize = healthSync.get() ? size.get() * healthRatio : size.get();
        double w = ((b.maxX - b.minX) / 2) + currentSize;
        
        double time = (System.currentTimeMillis() / 1000.0) * spinSpeed.get();

        for (int i = 0; i < 4; i++) {
            double angle = (i * Math.PI / 2) + time;
            double ox = Math.cos(angle) * w;
            double oz = Math.sin(angle) * w;

            // Draw the 4 corner "Pillars"
            event.renderer.box(
                x + ox - 0.02, y, z + oz - 0.02,
                x + ox + 0.02, y + h, z + oz + 0.02,
                color.get(), color.get(), ShapeMode.Both, 0
            );
        }
    }
}
