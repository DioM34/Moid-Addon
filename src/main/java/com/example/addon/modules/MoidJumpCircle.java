package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public class MoidJumpCircle extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Curve { Linear, Smooth, Pop }

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder().name("max-radius").defaultValue(2.0).min(0.5).sliderMax(5.0).build());
    private final Setting<Double> expandSpeed = sg.add(new DoubleSetting.Builder().name("expand-speed").defaultValue(1.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Curve> curve = sg.add(new EnumSetting.Builder<Curve>().name("speed-curve").defaultValue(Curve.Pop).build());
    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(10).build());
    private final Setting<Double> yOffset = sg.add(new DoubleSetting.Builder().name("y-offset").defaultValue(0.05).min(0).max(1).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(255, 255, 255, 200)).build());
    
    private final Setting<Boolean> onlySelf = sg.add(new BoolSetting.Builder().name("only-self").defaultValue(true).build());
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .visible(() -> !onlySelf.get())
        .defaultValue(Set.of(EntityType.PLAYER))
        .build());

    private final List<Circle> circles = new ArrayList<>();
    private final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    public MoidJumpCircle() {
        super(AddonTemplate.CATEGORY, "moid-jump-circle", "Renders a visible ripple effect when entities jump.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        for (Entity entity : mc.world.getEntities()) {
            UUID uuid = entity.getUuid();
            boolean onGround = entity.isOnGround();
            
            // Logic for Only Self vs Entity List
            if (onlySelf.get()) {
                if (entity != mc.player) continue;
            } else {
                if (!entities.get().contains(entity.getType())) continue;
            }

            // Jump Detection
            if (wasOnGround.getOrDefault(uuid, false) && !onGround && entity.getVelocity().y > 0) {
                circles.add(new Circle(new Vec3d(entity.getX(), entity.getY() + yOffset.get(), entity.getZ())));
            }
            
            wasOnGround.put(uuid, onGround);
        }
        
        circles.removeIf(c -> c.progress >= 1.0);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (circles) {
            for (Circle c : circles) {
                c.render(event);
            }
        }
    }

    private class Circle {
        private final Vec3d pos;
        public double progress = 0;

        public Circle(Vec3d pos) {
            this.pos = pos;
        }

        public void render(Render3DEvent event) {
            progress += (expandSpeed.get() * 0.02);
            if (progress > 1.0) return;

            double easedProgress = switch (curve.get()) {
                case Pop -> Math.sqrt(progress);
                case Smooth -> progress * progress * (3 - 2 * progress);
                default -> progress;
            };

            double currentRadius = easedProgress * maxRadius.get();
            int alpha = (int) (color.get().a * (1.0 - progress));
            SettingColor drawCol = new SettingColor(color.get().r, color.get().g, color.get().b, alpha);

            // Manual Thickness: Draw offset rings to make it "bold"
            for (int t = 0; t < thickness.get(); t++) {
                double rOffset = t * 0.01; 
                for (int i = 0; i < 360; i += 10) {
                    double rad1 = Math.toRadians(i);
                    double rad2 = Math.toRadians(i + 10);
                    event.renderer.line(
                        pos.x + Math.cos(rad1) * (currentRadius + rOffset), pos.y, pos.z + Math.sin(rad1) * (currentRadius + rOffset),
                        pos.x + Math.cos(rad2) * (currentRadius + rOffset), pos.y, pos.z + Math.sin(rad2) * (currentRadius + rOffset),
                        drawCol
                    );
                }
            }
        }
    }
}
