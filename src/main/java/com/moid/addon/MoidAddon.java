package com.moid.addon;

import com.moid.addon.modules.*;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MoidAddon extends MeteorAddon {
    // Branded logger
    public static final Logger LOG = LoggerFactory.getLogger("Moid");
    public static final Category CATEGORY = new Category("Moid");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Moid Addon...");

        Modules m = Modules.get();
        
        // Movement
        m.add(new MoidFly());
        m.add(new VulcanWeb());
                
        // Visuals & HUD
        m.add(new MoidHUD());
        m.add(new MoidCircleESP());
        m.add(new MoidGhostESP());
        m.add(new MoidJumpCircle());
        m.add(new JumpMatrix());
        m.add(new MoidTrails());
        m.add(new MoidHitESP());
        m.add(new MoidHitParticles());
        m.add(new ProjectilePredictor());
        m.add(new TargetHUD());
        m.add(new LightESP());
        m.add(new FakeScoreboard());

        // Combat & Utility
        m.add(new MoidLag());
        m.add(new MoidKillAura());
        m.add(new MoidAutoClicker());
        m.add(new ACDetector());
        m.add(new GrimBlink());
        m.add(new Triggerbot());
        m.add(new AntiResourcePack());
        m.add(new GhostHand());
        m.add(new MoidAim());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        // MUST match your actual folder structure in src/main/java
        return "com.moid.addon"; 
    }
}