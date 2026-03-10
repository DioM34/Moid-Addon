package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;

public class MoidReach extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> minRange = sg.add(new DoubleSetting.Builder()
        .name("min-range")
        .defaultValue(3.1)
        .min(3.0).sliderMax(5.0).build());

    private final Setting<Double> maxRange = sg.add(new DoubleSetting.Builder()
        .name("max-range")
        .defaultValue(3.3)
        .min(3.0).sliderMax(5.0).build());

    private final Setting<Integer> hitDelay = sg.add(new IntSetting.Builder()
        .name("hit-delay-ms")
        .description("Delay between reach-checks to look human.")
        .defaultValue(50)
        .min(0).sliderMax(500).build());

    private long lastHitTime = 0;

    public MoidReach() {
        super(AddonTemplate.CATEGORY, "moid-reach", "Randomized reach to bypass anti-cheats.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || !mc.options.attackKey.isPressed()) return;

        if (System.currentTimeMillis() - lastHitTime < hitDelay.get()) return;

        // Randomize the reach for this specific tick
        double currentReach = ThreadLocalRandom.current().nextDouble(minRange.get(), maxRange.get());
        
        Entity target = getReachTarget(currentReach);
        
        if (target != null && mc.player.getAttackCooldownProgress(0.5f) >= 1) {
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);
            lastHitTime = System.currentTimeMillis();
        }
    }

    private Entity getReachTarget(double dist) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d look = mc.player.getRotationVec(1.0f);
        Vec3d max = eye.add(look.x * dist, look.y * dist, look.z * dist);
        Box box = mc.player.getBoundingBox().expand(dist);

        EntityHitResult hit = ProjectileUtil.raycast(
            mc.player, eye, max, box, 
            e -> !e.isSpectator() && e.canHit(), 
            dist * dist
        );

        return (hit != null) ? hit.getEntity() : null;
    }
}
