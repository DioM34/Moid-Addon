package com.moid.addon.modules;

import com.moid.addon.MoidAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class MoidGhostESP extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgVisuals = settings.createGroup("Visuals");
    private final SettingGroup sgTrail = settings.createGroup("Trail");

    // --- Settings ---
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Integer> amount = sg.add(new IntSetting.Builder().name("soul-amount").defaultValue(3).min(1).max(8).build());
    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder().name("speed").defaultValue(1.5).min(0).sliderMax(5).build());
    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(0.8).min(0.1).sliderMax(2.0).build());
    private final Setting<Double> size = sg.add(new DoubleSetting.Builder().name("soul-size").defaultValue(0.04).min(0.01).sliderMax(0.2).build());
    
    private final Setting<SettingColor> color = sgVisuals.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(165, 3, 252, 255)).build());
    private final Setting<Double> wobbleHeight = sgVisuals.add(new DoubleSetting.Builder().name("figure-8-height").defaultValue(0.6).min(0).sliderMax(2.0).build());

    private final Setting<Boolean> smoothTrails = sgTrail.add(new BoolSetting.Builder().name("smooth-splines").defaultValue(true).build());
    private final Setting<Integer> trailLength = sgTrail.add(new IntSetting.Builder().name("trail-length").defaultValue(25).min(5).sliderMax(100).build());
    private final Setting<Integer> splineSegments = sgTrail.add(new IntSetting.Builder().name("spline-detail").defaultValue(4).min(1).max(10).visible(smoothTrails::get).build());

    // Performance Optimized Data Structures
    private final Map<UUID, List<ArrayDeque<Vec3d>>> ghostData = new HashMap<>();

    public MoidGhostESP() {
        super(MoidAddon.CATEGORY, "moid-ghost-esp", "Grim-style orbital GhostESP.");
    }

    @Override
    public void onDeactivate() {
        ghostData.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;

        // Cleanup: Remove trails for entities that no longer exist using public API
        Set<UUID> currentUuids = new HashSet<>();
        for (Entity entity : mc.world.getEntities()) {
            currentUuids.add(entity.getUuid());
        }
        ghostData.keySet().removeIf(uuid -> !currentUuids.contains(uuid));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        double time = ((System.currentTimeMillis() % 1000000) / 1000.0) * speed.get();

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entities.get().contains(entity.getType())) continue;

            Vec3d pos = entity.getLerpedPos(event.tickDelta);
            Box box = entity.getBoundingBox();
            double centerY = pos.y + (box.maxY - box.minY) / 2.0;

            List<ArrayDeque<Vec3d>> entityTrails = ghostData.computeIfAbsent(entity.getUuid(), k -> new ArrayList<>());

            for (int i = 0; i < amount.get(); i++) {
                if (entityTrails.size() <= i) entityTrails.add(new ArrayDeque<>());
                ArrayDeque<Vec3d> trail = entityTrails.get(i);

                double offset = (i * (Math.PI * 2 / amount.get()));
                double t = time + offset;
                
                // Figure-8 Orbit
                double ox = pos.x + Math.cos(t) * radius.get();
                double oz = pos.z + (Math.sin(2 * t) / 2.0) * radius.get();
                double oy = centerY + Math.sin(t) * wobbleHeight.get();
                
                Vec3d currentSoulPos = new Vec3d(ox, oy, oz);

                trail.addFirst(currentSoulPos);
                while (trail.size() > trailLength.get()) trail.removeLast();

                renderGhostTrail(event, trail);
                renderSoul(event, currentSoulPos);
            }
        }
    }

    private void renderGhostTrail(Render3DEvent event, ArrayDeque<Vec3d> points) {
        if (points.size() < 2) return;
        List<Vec3d> pointList = new ArrayList<>(points);
        SettingColor col = color.get();

        if (smoothTrails.get() && pointList.size() > 3) {
            for (int i = 0; i < pointList.size() - 3; i++) {
                Vec3d p0 = pointList.get(i);
                Vec3d p1 = pointList.get(i + 1);
                Vec3d p2 = pointList.get(i + 2);
                Vec3d p3 = pointList.get(i + 3);

                for (int j = 0; j < splineSegments.get(); j++) {
                    double t1 = (double) j / splineSegments.get();
                    double t2 = (double) (j + 1) / splineSegments.get();

                    Vec3d v1 = getCatmullRomPoint(p0, p1, p2, p3, t1);
                    Vec3d v2 = getCatmullRomPoint(p0, p1, p2, p3, t2);

                    double progress = (double) (i * splineSegments.get() + j) / (pointList.size() * splineSegments.get());
                    int alpha = (int) (col.a * (1.0 - progress));
                    
                    event.renderer.line(v1.x, v1.y, v1.z, v2.x, v2.y, v2.z, new SettingColor(col.r, col.g, col.b, alpha));
                }
            }
        } else {
            Vec3d last = null;
            int i = 0;
            for (Vec3d p : points) {
                if (last != null) {
                    double pct = 1.0 - ((double) i / points.size());
                    event.renderer.line(last.x, last.y, last.z, p.x, p.y, p.z, new SettingColor(col.r, col.g, col.b, (int) (col.a * pct)));
                }
                last = p;
                i++;
            }
        }
    }

    private void renderSoul(Render3DEvent event, Vec3d pos) {
        double s = size.get();
        // Fixed 1.21 Box Signature
        event.renderer.box(pos.x - s, pos.y - s, pos.z - s, pos.x + s, pos.y + s, pos.z + s, color.get(), color.get(), ShapeMode.Both, 0);
    }

    private Vec3d getCatmullRomPoint(Vec3d p0, Vec3d p1, Vec3d p2, Vec3d p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        double x = 0.5 * ((2 * p1.x) + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3);
        double y = 0.5 * ((2 * p1.y) + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3);
        double z = 0.5 * ((2 * p1.z) + (-p0.z + p2.z) * t + (2 * p0.z - 5 * p1.z + 4 * p2.z - p3.z) * t2 + (-p0.z + 3 * p1.z - 3 * p2.z + p3.z) * t3);
        return new Vec3d(x, y, z);
    }
}