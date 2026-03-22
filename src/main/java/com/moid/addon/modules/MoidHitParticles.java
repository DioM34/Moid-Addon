package com.moid.addon.modules;

import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import com.moid.addon.MoidAddon;

public class MoidHitParticles extends Module {

    public enum Shape { Square, Star, Circle, Triangle, Heart }

    // Heart parametric equation constants
    private static final double HEART_A = 0.8125;
    private static final double HEART_B = 0.3125;
    private static final double HEART_C = 0.125;
    private static final double HEART_D = 0.0625;

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Set<EntityType<?>>> entities = sg.add(
        new EntityTypeListSetting.Builder()
            .name("entities")
            .defaultValue(Set.of(EntityType.PLAYER))
            .build());

    private final Setting<Shape> shape = sg.add(
        new EnumSetting.Builder<Shape>()
            .name("shape")
            .defaultValue(Shape.Star)
            .build());

    private final Setting<SettingColor> color = sg.add(
        new ColorSetting.Builder()
            .name("color")
            .defaultValue(new SettingColor(255, 0, 0, 255))
            .build());

    private final Setting<Boolean> fill = sg.add(
        new BoolSetting.Builder()
            .name("fill")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> glow = sg.add(
        new BoolSetting.Builder()
            .name("glow")
            .defaultValue(true)
            .build());

    private final Setting<Double> size = sg.add(
        new DoubleSetting.Builder()
            .name("size")
            .defaultValue(0.15).min(0.01).sliderMax(0.5)
            .build());

    private final Setting<Double> range = sg.add(
        new DoubleSetting.Builder()
            .name("range")
            .defaultValue(0.2).min(0.01).sliderMax(1.0)
            .build());

    private final Setting<Double> rotationSpeed = sg.add(
        new DoubleSetting.Builder()
            .name("rotation-speed")
            .defaultValue(5.0).min(0).sliderMax(20.0)
            .build());

    private final Setting<Integer> amount = sg.add(
        new IntSetting.Builder()
            .name("amount")
            .defaultValue(10).min(1).sliderMax(50)
            .build());

    private final Setting<Integer> lifeTime = sg.add(
        new IntSetting.Builder()
            .name("life-time")
            .defaultValue(40).min(1).sliderMax(100)
            .build());

    private final List<Particle> particles = new ArrayList<>();
    private final List<Particle> toAdd     = new ArrayList<>();
    private final Color renderColor        = new Color();

    public MoidHitParticles() {
        super(MoidAddon.CATEGORY, "moid-hit-particles",
            "Camera-facing 2D hit particles with glow.");
    }

    @Override
    public void onDeactivate() {
        particles.clear();
        toAdd.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity target = event.entity;
        if (target == null) return;
        if (target == mc.player) return;
        if (!entities.get().contains(target.getType())) return;

        Vec3d pos = new Vec3d(
            target.getX(),
            target.getY() + (target.getEyeHeight(target.getPose()) / 1.5),
            target.getZ());

        for (int i = 0; i < amount.get(); i++) {
            toAdd.add(new Particle(pos));
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!toAdd.isEmpty()) {
            particles.addAll(toAdd);
            toAdd.clear();
        }

        particles.removeIf(p -> {
            p.lastPos   = p.pos;
            p.pos       = p.pos.add(p.vel);
            p.vel       = new Vec3d(
                p.vel.x * 0.98,
                p.vel.y - 0.003,
                p.vel.z * 0.98);
            p.rotation += rotationSpeed.get();
            return --p.life <= 0;
        });
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (particles.isEmpty()) return;

        // Build a proper camera-facing billboard basis
        // using the camera's view direction directly
        // This fixes the flipping at extreme pitch angles
        float yawRad   = (float) Math.toRadians(
            mc.getEntityRenderDispatcher().camera.getYaw());
        float pitchRad = (float) Math.toRadians(
            mc.getEntityRenderDispatcher().camera.getPitch());

        // Forward vector (into screen)
        Vec3d forward = new Vec3d(
            -Math.sin(yawRad) * Math.cos(pitchRad),
            -Math.sin(pitchRad),
             Math.cos(yawRad) * Math.cos(pitchRad));

        // World up
        Vec3d worldUp = new Vec3d(0, 1, 0);

        // Right = forward x worldUp — normalized
        Vec3d right = forward.crossProduct(worldUp).normalize();

        // True up = right x forward — normalized
        // This gives a stable up vector regardless of pitch
        Vec3d up = right.crossProduct(forward).normalize();

        // Cache color once per frame
        SettingColor col = color.get();

        for (Particle p : particles) {
            float alpha = (float) p.life / lifeTime.get();
            double x = p.lastPos.x + (p.pos.x - p.lastPos.x) * event.tickDelta;
            double y = p.lastPos.y + (p.pos.y - p.lastPos.y) * event.tickDelta;
            double z = p.lastPos.z + (p.pos.z - p.lastPos.z) * event.tickDelta;
            double s = size.get() * alpha;

            if (glow.get()) {
                renderColor.set(col);
                renderColor.a = (int)(col.a * alpha * 0.3);
                drawShape(event, x, y, z, s * 1.5,
                    p.rotation, right, up, true);
            }

            renderColor.set(col);
            renderColor.a = (int)(col.a * alpha);
            drawShape(event, x, y, z, s,
                p.rotation, right, up, fill.get());
        }
    }

    private void drawShape(Render3DEvent event,
                           double x, double y, double z,
                           double s, double rotation,
                           Vec3d right, Vec3d up,
                           boolean isFilled) {
        switch (shape.get()) {
            case Star  -> drawStar(event, x, y, z, s, rotation, right, up, isFilled);
            case Heart -> drawHeart(event, x, y, z, s, rotation, right, up, isFilled);
            default    -> drawPolygon(event, x, y, z, s, rotation, right, up, isFilled);
        }
    }

    private void drawPolygon(Render3DEvent event,
                             double x, double y, double z,
                             double s, double rotation,
                             Vec3d right, Vec3d up,
                             boolean isFilled) {
        int points = switch (shape.get()) {
            case Triangle -> 3;
            case Circle   -> 16;
            default       -> 4;
        };

        double step = 360.0 / points;
        Vec3d first = null;
        Vec3d last  = null;

        for (int i = 0; i <= points; i++) {
            double angle  = Math.toRadians(i * step + rotation);
            Vec3d current = project(x, y, z,
                Math.cos(angle) * s,
                Math.sin(angle) * s,
                right, up);

            if (first == null) first = current;
            if (last != null) connectPoints(event, x, y, z,
                last, current, isFilled);
            last = current;
        }

        closeShape(event, x, y, z, first, last, isFilled);
    }

    private void drawStar(Render3DEvent event,
                          double x, double y, double z,
                          double s, double rotation,
                          Vec3d right, Vec3d up,
                          boolean isFilled) {
        int points   = 5;
        double outer = s;
        double inner = s * 0.4;
        Vec3d first  = null;
        Vec3d last   = null;

        for (int i = 0; i <= points * 2; i++) {
            double angle  = Math.toRadians(
                i * (360.0 / (points * 2)) + rotation);
            double radius = (i % 2 == 0) ? outer : inner;
            Vec3d current = project(x, y, z,
                Math.cos(angle) * radius,
                Math.sin(angle) * radius,
                right, up);

            if (first == null) first = current;
            if (last != null) connectPoints(event, x, y, z,
                last, current, isFilled);
            last = current;
        }

        closeShape(event, x, y, z, first, last, isFilled);
    }

    private void drawHeart(Render3DEvent event,
                           double x, double y, double z,
                           double s, double rotation,
                           Vec3d right, Vec3d up,
                           boolean isFilled) {
        int steps   = 32;
        Vec3d first = null;
        Vec3d last  = null;
        double cosR = Math.cos(Math.toRadians(rotation));
        double sinR = Math.sin(Math.toRadians(rotation));

        for (int i = 0; i <= steps; i++) {
            double t  = (2 * Math.PI * i) / steps;
            double hx = s * 0.5 * Math.pow(Math.sin(t), 3);
            double hy = s * 0.5 * (HEART_A * Math.cos(t)
                - HEART_B * Math.cos(2 * t)
                - HEART_C * Math.cos(3 * t)
                - HEART_D * Math.cos(4 * t));

            // Rotate heart
            double rx = hx * cosR - hy * sinR;
            double ry = hx * sinR + hy * cosR;

            Vec3d current = project(x, y, z, rx, ry, right, up);

            if (first == null) first = current;
            if (last != null) connectPoints(event, x, y, z,
                last, current, isFilled);
            last = current;
        }

        closeShape(event, x, y, z, first, last, isFilled);
    }

    // Project 2D billboard coords into 3D world space
    private Vec3d project(double x, double y, double z,
                          double rx, double ry,
                          Vec3d right, Vec3d up) {
        return new Vec3d(x, y, z)
            .add(right.multiply(rx))
            .add(up.multiply(ry));
    }

    private void connectPoints(Render3DEvent event,
                               double cx, double cy, double cz,
                               Vec3d a, Vec3d b, boolean fill) {
        if (fill) event.renderer.quad(
            cx, cy, cz, cx, cy, cz,
            a.x, a.y, a.z,
            b.x, b.y, b.z,
            renderColor);
        event.renderer.line(
            a.x, a.y, a.z,
            b.x, b.y, b.z,
            renderColor);
    }

    private void closeShape(Render3DEvent event,
                            double cx, double cy, double cz,
                            Vec3d first, Vec3d last,
                            boolean fill) {
        if (first != null && last != null && !first.equals(last)) {
            connectPoints(event, cx, cy, cz, last, first, fill);
        }
    }

    private class Particle {
        Vec3d pos, lastPos, vel;
        int life;
        double rotation;

        public Particle(Vec3d origin) {
            this.pos      = origin;
            this.lastPos  = origin;
            double r      = range.get();
            this.vel      = new Vec3d(
                (ThreadLocalRandom.current().nextDouble() - 0.5) * r,
                ThreadLocalRandom.current().nextDouble() * 0.1,
                (ThreadLocalRandom.current().nextDouble() - 0.5) * r);
            this.life     = lifeTime.get();
            this.rotation = ThreadLocalRandom.current().nextDouble() * 360;
        }
    }
}