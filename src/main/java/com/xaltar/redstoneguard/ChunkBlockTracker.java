package com.xaltar.redstoneguard;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the count of limited block types per chunk in memory.
 * Fast, thread-safe, and memory-efficient.
 */
public class ChunkBlockTracker {

    private final XaltarRedstoneGuard plugin;
    // Map: ChunkKey -> (Material -> count)
    private final ConcurrentHashMap<ChunkKey, Map<Material, AtomicInteger>> chunkBlockCounts = new ConcurrentHashMap<>();
    // Cached limited materials and their limits
    private volatile Map<Material, Integer> limitMap = new EnumMap<>(Material.class);
    // Fast O(1) lookup set for limited materials
    private volatile EnumSet<Material> limitedMaterialSet = EnumSet.noneOf(Material.class);

    public ChunkBlockTracker(XaltarRedstoneGuard plugin) {
        this.plugin = plugin;
        reloadLimits();
    }

    /**
     * Reloads the limit map from config.
     */
    public void reloadLimits() {
        Map<Material, Integer> newLimits = new EnumMap<>(Material.class);
        if (plugin.getConfig().isConfigurationSection("block-limits")) {
            for (String key : plugin.getConfig().getConfigurationSection("block-limits").getKeys(false)) {
                if (key.equals("enabled") || key.equals("message")) {
                    continue;
                }
                Material material = Material.matchMaterial(key);
                if (material != null) {
                    int limit = plugin.getConfig().getInt("block-limits." + key, 0);
                    if (limit > 0) {
                        newLimits.put(material, limit);
                    }
                } else {
                    plugin.getLogger().warning("Material inconnu dans block-limits: " + key);
                }
            }
        }
        this.limitMap = newLimits;
        this.limitedMaterialSet = EnumSet.copyOf(newLimits.keySet());
    }

    /**
     * Fast O(1) check if a material is limited.
     */
    public boolean isLimitedMaterial(Material material) {
        return limitedMaterialSet.contains(material);
    }

    /**
     * Scans an entire chunk and counts all limited block types.
     * Uses ChunkSnapshot for much faster read-only access without
     * creating Block objects or crossing into the server thread.
     */
    public void scanChunk(Chunk chunk) {
        ChunkKey key = ChunkKey.fromChunk(chunk);
        Material[] limited = getLimitedMaterials();
        if (limited.length == 0) {
            chunkBlockCounts.remove(key);
            return;
        }

        Map<Material, AtomicInteger> counts = new EnumMap<>(Material.class);
        for (Material material : limited) {
            counts.put(material, new AtomicInteger(0));
        }

        ChunkSnapshot snapshot = chunk.getChunkSnapshot();
        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Material type = snapshot.getBlockType(x, y, z);
                    AtomicInteger counter = counts.get(type);
                    if (counter != null) {
                        counter.incrementAndGet();
                    }
                }
            }
        }

        chunkBlockCounts.put(key, counts);
    }

    /**
     * Attempts to increment the block count for a chunk.
     *
     * @return true if the placement is allowed, false if the limit is reached
     */
    public boolean tryIncrement(ChunkKey key, Material material) {
        Map<Material, AtomicInteger> counts = chunkBlockCounts.get(key);
        if (counts == null) {
            // Chunk not tracked yet, allow and initialize lazily
            counts = new EnumMap<>(Material.class);
            for (Material m : getLimitedMaterials()) {
                counts.put(m, new AtomicInteger(0));
            }
            Map<Material, AtomicInteger> existing = chunkBlockCounts.putIfAbsent(key, counts);
            if (existing != null) {
                counts = existing;
            }
        }

        AtomicInteger counter = counts.get(material);
        if (counter == null) {
            return true; // Not a limited material
        }

        int limit = getLimit(material);
        if (limit <= 0) {
            return true; // Limit disabled
        }

        int current = counter.get();
        if (current >= limit) {
            return false;
        }

        counter.incrementAndGet();
        return true;
    }

    /**
     * Decrements the block count for a chunk (e.g., on block break).
     */
    public void decrement(ChunkKey key, Material material) {
        Map<Material, AtomicInteger> counts = chunkBlockCounts.get(key);
        if (counts == null) return;

        AtomicInteger counter = counts.get(material);
        if (counter != null) {
            counter.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    /**
     * Removes a chunk from tracking to free memory.
     */
    public void removeChunk(ChunkKey key) {
        chunkBlockCounts.remove(key);
    }

    /**
     * Returns the configured limit for a material.
     */
    public int getLimit(Material material) {
        return limitMap.getOrDefault(material, 0);
    }

    /**
     * Returns all materials that have a configured limit.
     */
    public Material[] getLimitedMaterials() {
        return limitMap.keySet().toArray(new Material[0]);
    }

    /**
     * Returns the current count for a material in a chunk.
     */
    public int getCount(ChunkKey key, Material material) {
        Map<Material, AtomicInteger> counts = chunkBlockCounts.get(key);
        if (counts == null) return 0;
        AtomicInteger counter = counts.get(material);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Returns a snapshot of all limits (Material -> limit).
     */
    public Map<Material, Integer> getAllLimits() {
        return new EnumMap<>(limitMap);
    }

    /**
     * Returns a snapshot of all counts for a given chunk.
     */
    public Map<Material, Integer> getAllCounts(ChunkKey key) {
        Map<Material, Integer> result = new EnumMap<>(Material.class);
        Map<Material, AtomicInteger> counts = chunkBlockCounts.get(key);
        if (counts == null) {
            // Return zeros for all limited materials
            for (Material m : getLimitedMaterials()) {
                result.put(m, 0);
            }
            return result;
        }
        for (Map.Entry<Material, AtomicInteger> entry : counts.entrySet()) {
            result.put(entry.getKey(), entry.getValue().get());
        }
        return result;
    }

    /**
     * Rescans all currently loaded chunks to update counts after a reload.
     */
    public void rescanAllLoadedChunks() {
        chunkBlockCounts.clear();
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scanChunk(chunk);
            }
        }
    }

    public void shutdown() {
        chunkBlockCounts.clear();
    }
}
