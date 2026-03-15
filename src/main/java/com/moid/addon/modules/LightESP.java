package com.moid.addon.modules;

import com.moid.addon.MoidAddon;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.LightType;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class LightESP extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgFilter = settings.createGroup("Filters");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Integer> chunkRadius = sgGeneral.add(new IntSetting.Builder().name("chunk-radius").defaultValue(4).min(1).max(16).sliderMax(16).build());
    private final Setting<Integer> minY = sgGeneral.add(new IntSetting.Builder().name("min-y").defaultValue(-64).min(-64).max(319).build());
    private final Setting<Integer> maxY = sgGeneral.add(new IntSetting.Builder().name("max-y").defaultValue(100).min(-64).max(319).build());
    private final Setting<Integer> minLightLevel = sgGeneral.add(new IntSetting.Builder().name("min-light-level").defaultValue(8).min(0).max(15).build());
    private final Setting<Boolean> onlySourceBlocks = sgFilter.add(new BoolSetting.Builder().name("only-source-blocks").defaultValue(true).build());
    private final Setting<Boolean> filterNaturalLight = sgFilter.add(new BoolSetting.Builder().name("filter-natural-light").defaultValue(false).build());
    private final Setting<Boolean> showTorches = sgFilter.add(new BoolSetting.Builder().name("torches").defaultValue(true).build());
    private final Setting<Boolean> showGlowstone = sgFilter.add(new BoolSetting.Builder().name("glowstone-style").defaultValue(true).build());
    private final Setting<Boolean> showRedstone = sgFilter.add(new BoolSetting.Builder().name("redstone-lights").defaultValue(true).build());
    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>().name("shape-mode").defaultValue(ShapeMode.Both).build());
    private final Setting<Boolean> thermalColors = sgRender.add(new BoolSetting.Builder().name("thermal-colors").defaultValue(true).build());
    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder().name("side-color").defaultValue(new SettingColor(255, 255, 0, 75)).visible(() -> !thermalColors.get()).build());
    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder().name("line-color").defaultValue(new SettingColor(255, 255, 0, 255)).visible(() -> !thermalColors.get()).build());

    private final Map<BlockPos, Integer> lightCache = new HashMap<>();
    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private long lastScanTime = 0;
    private final Color workingColor = new Color();

    public LightESP() {
        super(MoidAddon.CATEGORY, "light-esp", "High-performance light source ESP. (Credit: NNPG)");
    }

    @Override
    public void onActivate() {
        synchronized (lightCache) {
            lightCache.clear();
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;

        if (System.currentTimeMillis() - lastScanTime >= 1000 && !isScanning.get()) {
            lastScanTime = System.currentTimeMillis();
            CompletableFuture.runAsync(this::scanForLights);
        }

        renderCachedLights(event);
    }

    private void scanForLights() {
        isScanning.set(true);
        try {
            ChunkPos playerChunkPos = mc.player.getChunkPos();
            int radius = chunkRadius.get();
            Map<BlockPos, Integer> newCache = new HashMap<>();
            BlockPos.Mutable mutablePos = new BlockPos.Mutable();

            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    Chunk chunk = mc.world.getChunk(playerChunkPos.x + x, playerChunkPos.z + z);
                    if (chunk != null && chunk.getStatus().isAtLeast(ChunkStatus.FULL)) {
                        for (int blockX = 0; blockX < 16; blockX++) {
                            for (int blockZ = 0; blockZ < 16; blockZ++) {
                                for (int blockY = minY.get(); blockY <= maxY.get(); blockY++) {
                                    mutablePos.set(chunk.getPos().getStartX() + blockX, blockY, chunk.getPos().getStartZ() + blockZ);
                                    
                                    int blockLight = mc.world.getLightLevel(LightType.BLOCK, mutablePos);
                                    if (blockLight < minLightLevel.get()) continue;

                                    BlockState state = chunk.getBlockState(mutablePos);
                                    if (onlySourceBlocks.get() && state.getLuminance() <= 0) continue;
                                    if (!passesFilters(state.getBlock())) continue;

                                    newCache.put(mutablePos.toImmutable(), blockLight);
                                }
                            }
                        }
                    }
                }
            }

            synchronized (lightCache) {
                lightCache.clear();
                lightCache.putAll(newCache);
            }
        } finally {
            isScanning.set(false);
        }
    }

    private boolean passesFilters(Block block) {
        if (filterNaturalLight.get() && (block == Blocks.LAVA || block == Blocks.FIRE || block == Blocks.MAGMA_BLOCK)) return false;
        if (!showTorches.get() && (block == Blocks.TORCH || block == Blocks.LANTERN || block == Blocks.SOUL_TORCH)) return false;
        if (!showGlowstone.get() && (block == Blocks.GLOWSTONE || block == Blocks.SEA_LANTERN)) return false;
        if (!showRedstone.get() && (block == Blocks.REDSTONE_LAMP || block == Blocks.REDSTONE_TORCH)) return false;
        return true;
    }

    private void renderCachedLights(Render3DEvent event) {
        synchronized (lightCache) {
            for (Map.Entry<BlockPos, Integer> entry : lightCache.entrySet()) {
                BlockPos pos = entry.getKey();
                int light = entry.getValue();

                if (thermalColors.get()) {
                    updateThermalColor(light);
                    event.renderer.box(pos, workingColor, workingColor, shapeMode.get(), 0);
                } else {
                    event.renderer.box(pos, sideColor.get(), lineColor.get(), shapeMode.get(), 0);
                }
            }
        }
    }

    private void updateThermalColor(int lightLevel) {
        float f = lightLevel / 15.0f;
        int r = (int) (f * 255);
        int g = (int) ((1 - Math.abs(f - 0.5) * 2) * 255);
        int b = (int) ((1 - f) * 255);
        workingColor.set(r, g, b, 100);
    }
}