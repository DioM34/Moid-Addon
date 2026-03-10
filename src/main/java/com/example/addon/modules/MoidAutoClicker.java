package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import java.util.concurrent.ThreadLocalRandom;

public class MoidAutoClicker extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    public enum Mode { Static, Jitter }

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>().name("mode").defaultValue(Mode.Jitter).build());
    private final Setting<Integer> minCps = sg.add(new IntSetting.Builder().name("min-cps").defaultValue(8).min(1).max(20).build());
    private final Setting<Integer> maxCps = sg.add(new IntSetting.Builder().name("max-cps").defaultValue(12).min(1).max(20).build());
    
    private final Setting<Double> jitterIntensity = sg.add(new DoubleSetting.Builder()
        .name("jitter-intensity")
        .visible(() -> mode.get() == Mode.Jitter)
        .defaultValue(0.5).min(0).max(1).build());

    private final Setting<Boolean> onlyOnTarget = sg.add(new BoolSetting.Builder()
        .name("only-on-target")
        .description("Only clicks if you are looking at an entity.")
        .defaultValue(false).build());

    private long lastClickTime = 0;
    private long nextDelay = 0;

    public MoidAutoClicker() {
        super(AddonTemplate.CATEGORY, "moid-auto-clicker", "Advanced randomized clicking.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.currentScreen != null) return;

        // Check if we are holding the attack key
        if (mc.options.attackKey.isPressed()) {
            if (onlyOnTarget.get() && mc.targetedEntity == null) return;

            long now = System.currentTimeMillis();

            if (now - lastClickTime >= nextDelay) {
                doClick();

                // Calculate Base Delay
                int targetCps = ThreadLocalRandom.current().nextInt(minCps.get(), maxCps.get() + 1);
                long baseDelay = 1000 / targetCps;

                // Apply Mode Logic
                if (mode.get() == Mode.Jitter) {
                    // Jitter creates high variance
                    double intensity = jitterIntensity.get() * 50; 
                    nextDelay = baseDelay + (long) ThreadLocalRandom.current().nextDouble(-intensity, intensity);
                } else {
                    // Static just adds a tiny human offset
                    nextDelay = baseDelay + ThreadLocalRandom.current().nextInt(-10, 10);
                }

                lastClickTime = now;
            }
        }
    }

    private void doClick() {
        // This forces the game to treat it as a real physical click
        mc.player.swingHand(Hand.MAIN_HAND);
        
        if (mc.targetedEntity != null) {
            mc.interactionManager.attackEntity(mc.player, mc.targetedEntity);
        } else if (mc.crosshairTarget != null) {
            // This handles clicking blocks or empty air
            mc.interactionManager.attackBlock(mc.player.getBlockPos(), mc.player.getHorizontalFacing());
        }
    }
}
