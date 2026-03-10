package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;
import java.util.concurrent.ThreadLocalRandom;

public class MoidVelocity extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> horizontal = sg.add(new DoubleSetting.Builder().name("horizontal").defaultValue(0.8).min(0).sliderMax(1).build());
    private final Setting<Double> vertical = sg.add(new DoubleSetting.Builder().name("vertical").defaultValue(1.0).min(0).sliderMax(1).build());
    private final Setting<Integer> chance = sg.add(new IntSetting.Builder().name("chance").defaultValue(100).min(0).max(100).build());

    private Vec3d lastVelocity = Vec3d.ZERO;

    public MoidVelocity() {
        super(AddonTemplate.CATEGORY, "moid-velocity", "Subtle motion-based knockback reduction.");
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;

        // Detect if we just took knockback (velocity spike)
        Vec3d currentVel = mc.player.getVelocity();
        
        // If the jump in velocity is sudden (like getting hit)
        if (Math.abs(currentVel.x - lastVelocity.x) > 0.05 || Math.abs(currentVel.z - lastVelocity.z) > 0.05) {
            
            // Probability check
            if (chance.get() == 100 || ThreadLocalRandom.current().nextInt(100) < chance.get()) {
                
                // Scale the velocity directly on the player object
                double newX = currentVel.x * horizontal.get();
                double newY = currentVel.y * vertical.get();
                double newZ = currentVel.z * horizontal.get();
                
                mc.player.setVelocity(newX, newY, newZ);
            }
        }
        
        lastVelocity = mc.player.getVelocity();
    }
}
