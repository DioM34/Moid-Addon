package com.moid.addon.modules;

import com.moid.addon.MoidAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.Set;

public class MoidAim extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBypass = settings.createGroup("Anti-Cheat / Humanization");

    public enum RotationMode { Tick, Frame }

    // --- General Settings ---
    private final Setting<RotationMode> mode = sgGeneral.add(new EnumSetting.Builder<RotationMode>().name("mode").description("Tick is safer, Frame is smoother.").defaultValue(RotationMode.Frame).build());
    private final Setting<Set<EntityType<?>>> entities = sgGeneral.add(new EntityTypeListSetting.Builder().name("entities").defaultValue(Set.of(EntityType.PLAYER)).build());
    private final Setting<Double> range = sgGeneral.add(new DoubleSetting.Builder().name("range").defaultValue(4.2).min(1).sliderMax(6).build());
    private final Setting<Double> fov = sgGeneral.add(new DoubleSetting.Builder().name("FOV").defaultValue(90.0).min(1).sliderMax(360).build());
    
    // --- Bypass Settings ---
    private final Setting<Double> smoothing = sgBypass.add(new DoubleSetting.Builder().name("smooth-speed").defaultValue(0.12).min(0.01).sliderMax(1.0).build());
    private final Setting<Double> jitter = sgBypass.add(new DoubleSetting.Builder().name("jitter-intensity").defaultValue(0.05).min(0).sliderMax(0.5).build());
    private final Setting<Boolean> dynamicPredict = sgBypass.add(new BoolSetting.Builder().name("ping-prediction").defaultValue(true).build());
    private final Setting<Boolean> silent = sgBypass.add(new BoolSetting.Builder().name("silent-rotations").defaultValue(false).build());

    private Entity target;
    private final Random random = new Random();
    private float serverYaw, serverPitch;
    
    public MoidAim() {
        super(MoidAddon.CATEGORY, "moid-aim", "Advanced aimbot with Tick and Frame-based smoothing.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        target = findBestTarget();
        
        // If we are in Tick mode, we apply the logic here.
        if (mode.get() == RotationMode.Tick) {
            runAimLogic(1.0f);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        // If we are in Frame mode, we apply logic every single frame for maximum smoothness.
        if (mode.get() == RotationMode.Frame && target != null) {
            runAimLogic(event.tickDelta);
        }
    }

    private void runAimLogic(float tickDelta) {
        if (target == null || mc.player == null) return;

        // 1. Target Positioning
        double heightOffset = target.getEyeHeight(target.getPose()) * 0.85;
        Vec3d targetVec = target.getLerpedPos(tickDelta).add(0, heightOffset, 0);

        // 2. Prediction Logic
        if (dynamicPredict.get() && mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            double pingTicks = (entry != null ? entry.getLatency() : 50) / 50.0;
            Vec3d vel = target.getVelocity();
            targetVec = targetVec.add(vel.x * pingTicks, vel.y * pingTicks, vel.z * pingTicks);
        }

        // 3. Angle Calculation
        float targetYaw = (float) Rotations.getYaw(targetVec);
        float targetPitch = (float) Rotations.getPitch(targetVec);

        // 4. Entropy (Jitter)
        if (jitter.get() > 0) {
            targetYaw += (random.nextFloat() - 0.5f) * jitter.get() * 4;
            targetPitch += (random.nextFloat() - 0.5f) * jitter.get() * 2;
        }

        // 5. Exponential Smoothing
        // Adjusting speed by tickDelta ensures it stays consistent regardless of FPS
        double adjustedSpeed = smoothing.get() * (mode.get() == RotationMode.Frame ? tickDelta : 1.0);
        serverYaw = updateAngle(serverYaw, targetYaw, adjustedSpeed);
        serverPitch = updateAngle(serverPitch, targetPitch, adjustedSpeed);

        // 6. Final Rotation
        if (silent.get()) {
            Rotations.rotate(serverYaw, serverPitch);
        } else {
            mc.player.setYaw(serverYaw);
            mc.player.setPitch(serverPitch);
        }
    }

    private float updateAngle(float current, float target, double speed) {
        float diff = MathHelper.wrapDegrees(target - current);
        return current + diff * (float) MathHelper.clamp(speed, 0, 1);
    }

    private Entity findBestTarget() {
        Entity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player || !(e instanceof LivingEntity) || !e.isAlive()) continue;
            if (!entities.get().contains(e.getType())) continue;
            
            double dist = mc.player.distanceTo(e);
            if (dist > range.get()) continue;

            float angle = (float) Math.abs(MathHelper.wrapDegrees(Rotations.getYaw(e) - mc.player.getYaw()));
            if (angle > fov.get() / 2) continue;

            if (angle < bestScore) {
                bestScore = angle;
                best = e;
            }
        }
        return best;
    }
}