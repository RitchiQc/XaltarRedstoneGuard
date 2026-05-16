package com.xaltar.redstoneguard;

import org.bukkit.entity.Item;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks items per chunk with a FIFO queue.
 * When the limit is exceeded, the oldest item is removed.
 */
public class ChunkItemTracker {

    private final XaltarRedstoneGuard plugin;
    // Map: ChunkKey -> FIFO queue of items
    private final ConcurrentHashMap<ChunkKey, Deque<Item>> chunkItemQueues = new ConcurrentHashMap<>();

    public ChunkItemTracker(XaltarRedstoneGuard plugin) {
        this.plugin = plugin;
    }

    /**
     * Adds an item to the chunk's queue.
     * If the limit is exceeded, removes and kills the oldest item.
     * Dead-item cleanup is done lazily only when the queue grows past the limit
     * to avoid per-spawn overhead.
     */
    public void addItem(ChunkKey key, Item item) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        int limit = plugin.getConfig().getInt("item-limits.max-items-per-chunk", 3000);
        if (limit <= 0) {
            return;
        }

        Deque<Item> queue = chunkItemQueues.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (queue) {
            queue.addLast(item);

            // Only clean up when we exceed the limit, not on every spawn
            if (queue.size() > limit) {
                // Batch-remove dead/invalid items first
                queue.removeIf(oldest -> oldest == null || !oldest.isValid() || oldest.isDead());

                // If still over limit after cleanup, remove oldest living items
                while (queue.size() > limit) {
                    Item oldest = queue.pollFirst();
                    if (oldest != null && oldest.isValid() && !oldest.isDead()) {
                        oldest.remove();
                    }
                }
            }
        }
    }

    /**
     * Removes an item from the chunk's queue (e.g., on despawn or pickup).
     */
    public void removeItem(ChunkKey key, Item item) {
        Deque<Item> queue = chunkItemQueues.get(key);
        if (queue == null) return;

        synchronized (queue) {
            queue.remove(item);
            if (queue.isEmpty()) {
                chunkItemQueues.remove(key);
            }
        }
    }

    /**
     * Removes a chunk from tracking to free memory.
     */
    public void removeChunk(ChunkKey key) {
        chunkItemQueues.remove(key);
    }

    /**
     * Returns the current item count for a chunk.
     */
    public int getCount(ChunkKey key) {
        Deque<Item> queue = chunkItemQueues.get(key);
        if (queue == null) return 0;

        synchronized (queue) {
            // Clean dead items before counting
            queue.removeIf(item -> item == null || !item.isValid() || item.isDead());
            return queue.size();
        }
    }

    public void shutdown() {
        chunkItemQueues.clear();
    }
}
