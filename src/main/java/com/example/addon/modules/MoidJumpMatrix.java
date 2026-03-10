package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public class MoidJumpMatrix extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> maxRadius = sg.add(new DoubleSetting.Builder().name("radius").defaultValue(4.0).min(1).sliderMax(10.0).build());
    private final Setting<Double> scanSpeed = sg.add(new DoubleSetting.Builder().name("scan-speed").defaultValue(2.0).min(0.1).sliderMax(5.0).build());
    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder().name("thickness").defaultValue(2).min(1).max(5).build());
    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder().name("color").defaultValue(new SettingColor(0, 255, 100, 200)).build());

    private final List<MatrixPulse> pulses = new ArrayList<>();
    private boolean wasOnGround = true;

    public MoidJumpMatrix() {
        super(AddonTemplate.CATEGORY, "moid-jump-matrix", "Renders a matrix-style block edge scan when jumping.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        if (wasOnGround && !mc.player.isOnGround() && mc.player.getVelocity().y > 0) {
            pulses.add(new MatrixPulse(new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        }
        wasOnGround = mc.player.isOnGround();
        
        pulses.removeIf(p -> p.age > 1.0);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        synchronized (pulses) {
            for (MatrixPulse p : pulses) {
                p.render(event);
            }
        }
    }

    private class MatrixPulse {
        private final Vec3d origin;
        public double age = 0;

        public MatrixPulse(Vec3d origin) {
            this.origin = origin;
        }

        public void render(Render3DEvent event) {
            age += (scanSpeed.get() * 0.01);
            if (age > 1.0) return;

            double currentRadius = age * maxRadius.get();
            int alpha = (int) (color.get().a * (1.0 - age));
            SettingColor drawCol = new SettingColor(color.get().r, color.get().g, color.get().b, alpha);

            BlockPos center = BlockPos.ofFloored(origin.x, origin.y, origin.z);
            int r = (int) Math.ceil(maxRadius.get());

            for (int x = -r; x <= r; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -r; z <= r; z++) {
                        BlockPos bp = center.add(x, y, z);
                        double dist = Math.sqrt(bp.getSquaredDistance(origin.x, origin.y, origin.z));

                        // Only render if the pulse wave is currently passing this block
                        if (dist < currentRadius && dist > currentRadius - 0.8) {
                            if (!mc.world.getBlockState(bp).isAir()) {
                                drawBlockEdges(event, bp, drawCol);
                            }
                        }
                    }
                }
            }
        }

        private void drawBlockEdges(Render3DEvent event, BlockPos p, SettingColor col) {
            double x1 = p.getX(), y1 = p.getY(), z1 = p.getZ();
            double x2 = x1 + 1, y2 = y1 + 1, z2 = z1 + 1;

            // Render edges with thickness offset
            for (int i = 0; i < thickness.get(); i++) {
                double o = i * 0.005;
                event.renderer.line(x1-o, y1-o, z1-o, x2+o, y1-o, z1-o, col);
                event.renderer.line(x1-o, y1-o, z1-o, x1-o, y1-o, z2+o, col);
                event.renderer.line(x2+o, y1-o, z1-o, x2+o, y1-o, z2+o, col);
                event.renderer.line(x1-o, y1-o, z2+o, x2+o, y1-o, z2+o, col);
            }
        }
    }
}
