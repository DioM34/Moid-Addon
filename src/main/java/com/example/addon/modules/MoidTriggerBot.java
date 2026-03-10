package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;

public class MoidTriggerBot extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> range = sg.add(new DoubleSetting.Builder().name("range").defaultValue(3.0).min(0).sliderMax(6).build());
    private final Setting<Double> fov = sg.add(new DoubleSetting.Builder().name("fov").defaultValue(3.0).min(0).sliderMax(30).build());
    private final Setting<Integer> delay = sg.add(new IntSetting.Builder().name("delay-ms").defaultValue(100).min(0).sliderMax(500).build());
    private final Setting<Boolean> clickOnly = sg.add(new BoolSetting.Builder().name("click-only").defaultValue(true).build());
    
    // Detailed Entity List
    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder()
        .name("entities")
        .defaultValue(Set.of(EntityType.PLAYER, EntityType.HORSE))
        .build());

    private long lastHit = 0;

    public MoidTriggerBot() {
        super(AddonTemplate.CATEGORY, "moid-trigger-bot", "Forces hits when aiming at specific entities.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (clickOnly.get() && !mc.options.attackKey.isPressed()) return;
        if (System.currentTimeMillis() - lastHit < delay.get()) return;

        for (Entity target : mc.world.getEntities()) {
            if (!(target instanceof LivingEntity) || target == mc.player || !target.isAlive()) continue;
            if (!entities.get().contains(target.getType())) continue;

            if (mc.player.distanceTo(target) <= range.get() && isAimingAt(target)) {
                if (mc.player.getAttackCooldownProgress(0.5f) >= 1) {
                    mc.interactionManager.attackEntity(mc.player, target);
                    mc.player.swingHand(Hand.MAIN_HAND);
                    lastHit = System.currentTimeMillis();
                    break;
                }
            }
        }
    }

    private boolean isAimingAt(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVec(1.0f);
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double angle = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, lookVec.dotProduct(targetVec)))));
        return angle <= fov.get();
    }
}
