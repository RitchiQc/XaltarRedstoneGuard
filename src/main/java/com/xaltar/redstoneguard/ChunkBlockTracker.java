package com.xaltar.redstoneguard;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.EnumMap;
import java.util.Map;
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

    public ChunkBlockTracker(XaltarRedstoneGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Scans an entire chunk and counts all limited block types.
     * Should be called from the chunk's regional thread (ChunkLoadEvent).
     */
    public void scanChunk(Chunk chunk) {
        ChunkKey key = ChunkKey.fromChunk(chunk);
        Map<Material, AtomicInteger> counts = new EnumMap<>(Material.class);

        for (Material material : getLimitedMaterials()) {
            counts.put(material, new AtomicInteger(0));
        }

        World world = chunk.getWorld();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        int chunkX = chunk.getX() << 4;
        int chunkZ = chunk.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    Block block = world.getBlockAt(chunkX + x, y, chunkZ + z);
                    Material type = block.getType();
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
        return switch (material) {
            case CRAFTER -> plugin.getConfig().getInt("block-limits.crafter", 4000);
            case DISPENSER -> plugin.getConfig().getInt("block-limits.dispenser", 4000);
            case DROPPER -> plugin.getConfig().getInt("block-limits.dropper", 4000);
            default -> 0;
        };
    }

    /**
     * Returns all materials that have a configured limit.
     */
    public Material[] getLimitedMaterials() {
        return new Material[]{
                Material.CRAFTER,
                Material.DISPENSER,
                Material.DROPPER
        };
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

    public void shutdown() {
        chunkBlockCounts.clear();
    }
}
