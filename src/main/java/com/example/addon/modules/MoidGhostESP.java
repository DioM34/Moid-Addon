package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import java.util.Set;

public class MoidGhostESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum GhostShape { Square, Circle, Triangle }

    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder()
        .name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());

    private final Setting<GhostShape> shape = sg.add(new EnumSetting.Builder<GhostShape>()
        .name("shape").defaultValue(GhostShape.Circle).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").defaultValue(new SettingColor(150, 0, 255, 200)).build());

    private final Setting<Integer> amount = sg.add(new IntSetting.Builder()
        .name("soul-amount").defaultValue(3).min(1).max(10).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").defaultValue(2.0).min(0).sliderMax(10).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").defaultValue(0.7).min(0.1).sliderMax(2.0).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("soul-size").defaultValue(0.05).min(0.01).sliderMax(0.5).build());

    private final Setting<Double> vOffset = sg.add(new DoubleSetting.Builder()
        .name("height-offset").description("0 = feet, 1 = head, 2 = above").defaultValue(0.5).min(-1).sliderMax(3).build());

    public MoidGhostESP() {
        super(AddonTemplate.CATEGORY, "moid-ghost-esp", "Soul-like orbits with customizable geometry.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        double time = (System.currentTimeMillis() / 1000.0) * speed.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;

            double x = MathHelper.lerp(event.tickDelta, entity.lastRenderX, entity.getX());
            double y = MathHelper.lerp(event.tickDelta, entity.lastRenderY, entity.getY());
            double z = MathHelper.lerp(event.tickDelta, entity.lastRenderZ, entity.getZ());
            
            Box box = entity.getBoundingBox();
            double height = (box.maxY - box.minY) * vOffset.get();

            for (int i = 0; i < amount.get(); i++) {
                double offset = (i * (Math.PI * 2 / amount.get()));
                double orbitTime = time + offset;

                double orbitX = x + Math.cos(orbitTime) * radius.get();
                double orbitZ = z + Math.sin(orbitTime) * radius.get();
                double orbitY = y + height + Math.sin(orbitTime * 0.5) * 0.2;

                renderSoul(event, orbitX, orbitY, orbitZ, color.get());
            }
        }
    }

    private void renderSoul(Render3DEvent event, double x, double y, double z, SettingColor col) {
        double s = size.get();
        switch (shape.get()) {
            case Square -> event.renderer.box(x - s, y - s, z - s, x + s, y + s, z + s, col, col, ShapeMode.Both, 0);
            case Circle -> {
                for (int i = 0; i < 360; i += 45) {
                    double r1 = Math.toRadians(i);
                    double r2 = Math.toRadians(i + 45);
                    event.renderer.line(x + Math.cos(r1) * s, y, z + Math.sin(r1) * s, x + Math.cos(r2) * s, y, z + Math.sin(r2) * s, col);
                }
            }
            case Triangle -> {
                for (int i = 0; i < 360; i += 120) {
                    double r1 = Math.toRadians(i);
                    double r2 = Math.toRadians(i + 120);
                    event.renderer.line(x + Math.cos(r1) * s, y, z + Math.sin(r1) * s, x + Math.cos(r2) * s, y, z + Math.sin(r2) * s, col);
                }
            }
        }
    }
}
