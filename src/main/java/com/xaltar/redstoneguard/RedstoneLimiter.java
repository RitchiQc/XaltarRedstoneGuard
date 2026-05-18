package com.xaltar.redstoneguard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Observer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RedstoneLimiter implements Listener {

    private final XaltarRedstoneGuard plugin;

    // ------------------------------------------------------------------------
    // Config cache — all values are loaded once and kept in volatile fields
    // to avoid expensive Bukkit ConfigurationSection lookups on the hot path.
    // ------------------------------------------------------------------------
    private volatile boolean limitingEnabled;
    private volatile boolean throttleEnabled;
    private volatile boolean cancelEnabled;
    private volatile boolean breakEnabled;
    private volatile boolean debugEnabled;
    private volatile long thresholdMs;
    private volatile long cleanupMaxAgeMs;

    // Per-world maps to avoid string concatenation in the hot path.
    // Key: packed block coordinates (x,y,z) as a single long.
    // Value: last tick timestamp in milliseconds.
    private final ConcurrentHashMap<String, ConcurrentHashMap<Long, Long>> tickLog = new ConcurrentHashMap<>();

    // Cache of materials to limit, built from config
    private volatile Set<Material> limitedMaterials = EnumSet.noneOf(Material.class);

    public RedstoneLimiter(XaltarRedstoneGuard plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    /**
     * Reloads all configuration values into memory cache.
     * Call this after config reload.
     */
    public void reloadConfig() {
        reloadLimitedMaterials();

        // Master switch (supports both new and legacy config keys)
        if (plugin.getConfig().isSet("limit-redstone")) {
            this.limitingEnabled = plugin.getConfig().getBoolean("limit-redstone", true);
        } else {
            this.limitingEnabled = plugin.getConfig().getBoolean("limit-observer", true);
        }

        // Throttle
        if (plugin.getConfig().isSet("throttle")) {
            this.throttleEnabled = plugin.getConfig().getBoolean("throttle", false);
        } else {
            this.throttleEnabled = plugin.getConfig().getBoolean("throttle-observer", false);
        }

        // Cancel
        if (plugin.getConfig().isSet("cancel-signal")) {
            this.cancelEnabled = plugin.getConfig().getBoolean("cancel-signal", false);
        } else {
            this.cancelEnabled = plugin.getConfig().getBoolean("cancel-observer", false);
        }

        // Break
        if (plugin.getConfig().isSet("break-block")) {
            this.breakEnabled = plugin.getConfig().getBoolean("break-block", false);
        } else {
            this.breakEnabled = plugin.getConfig().getBoolean("break-observer", false);
        }

        // Debug
        this.debugEnabled = plugin.getConfig().getBoolean("debug", false);

        // Pre-calculate threshold in milliseconds (1 tick = 50 ms)
        long thresholdTicks = plugin.getConfig().getLong("threshold-ticks", 2);
        this.thresholdMs = thresholdTicks * 50L;
        this.cleanupMaxAgeMs = this.thresholdMs + 5000L; // 100 ticks buffer
    }

    /**
     * Reloads the set of limited materials from the plugin configuration.
     */
    public void reloadLimitedMaterials() {
        List<String> materialNames = plugin.getConfig().getStringList("limited-materials");

        // Fallback: if the list is empty, use a sensible default set of redstone components
        if (materialNames == null || materialNames.isEmpty()) {
            materialNames = List.of(
                    "OBSERVER",
                    "COMPARATOR",
                    "REPEATER",
                    "REDSTONE_WIRE",
                    "REDSTONE_TORCH",
                    "REDSTONE_WALL_TORCH",
                    "PISTON",
                    "STICKY_PISTON",
                    "HOPPER",
                    "DROPPER",
                    "DISPENSER",
                    "DAYLIGHT_DETECTOR",
                    "LECTERN",
                    "TRIPWIRE_HOOK",
                    "TARGET"
            );
        }

        EnumSet<Material> newSet = EnumSet.noneOf(Material.class);
        for (String name : materialNames) {
            try {
                Material mat = Material.valueOf(name.toUpperCase());
                newSet.add(mat);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid material in limited-materials config: " + name);
            }
        }
        this.limitedMaterials = newSet;
    }

    /**
     * Packs block coordinates into a single long, matching Minecraft's BlockPos format.
     * This avoids any object allocation for map keys in the hot path.
     */
    private static long packKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // Master switch — cached volatile read, no config lookup
        if (!limitingEnabled) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();

        // Only handle configured materials
        if (!limitedMaterials.contains(material)) {
            return;
        }

        // Only handle rising edge (when signal starts / increases from 0)
        if (event.getNewCurrent() <= 0) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        long posKey = packKey(block.getX(), block.getY(), block.getZ());
        String worldName = block.getWorld().getName();

        ConcurrentHashMap<Long, Long> worldMap = tickLog.computeIfAbsent(worldName, k -> new ConcurrentHashMap<>());
        Long lastTime = worldMap.get(posKey);

        // All config values are cached — zero Bukkit config lookups on the hot path
        long threshold = thresholdMs;
        boolean throttle = throttleEnabled;
        boolean debug = debugEnabled;

        if (lastTime != null) {
            long timeDifference = currentTime - lastTime;

            // throttle: allow pulses at exactly threshold ticks, block only if faster
            // cancel: block if at or below threshold ticks
            boolean isViolation = throttle ? (timeDifference < threshold) : (timeDifference <= threshold);

            if (isViolation) {
                if (debug) {
                    plugin.getLogger().info("[RedstoneGuard] Clock detected at " + block.getX() + "," + block.getY() + "," + block.getZ()
                            + " (" + material + ") - " + timeDifference + "ms / threshold=" + threshold + "ms");
                }
                handleViolation(event, block, cancelEnabled, breakEnabled);
                if (!throttle) {
                    // cancel mode: log the current time so the next pulse is evaluated from now
                    worldMap.put(posKey, currentTime);
                }
                return;
            }
        }

        // Log current time for next check
        worldMap.put(posKey, currentTime);
    }

    private void handleViolation(BlockRedstoneEvent event, Block block, boolean cancel, boolean breakBlock) {
        // Cancel the redstone signal immediately
        if (cancel || throttleEnabled) {
            event.setNewCurrent(0);
            resetBlock(block);
        }

        // Remove the block without dropping an item
        if (breakBlock) {
            block.setType(Material.AIR);
        }
    }

    /**
     * Resets a block by re-applying its BlockData, which clears
     * its internal pulse state and effectively cancels the current output.
     * For observers this resets the pulse state.
     * Called synchronously from the event handler (already on the correct regional thread).
     */
    private void resetBlock(Block block) {
        BlockData data = block.getBlockData();
        if (data instanceof Observer) {
            // Re-apply the same BlockData to force the server to reset
            // the observer's internal pulse state without changing orientation
            block.setBlockData(data, false);
        }
        // For other redstone components, setNewCurrent(0) in the event is usually sufficient.
        // Additional per-type resets can be added here if needed.
    }

    /**
     * Removes stale entries to prevent memory leaks.
     * Safe to call from any thread thanks to ConcurrentHashMap and System.currentTimeMillis().
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        long maxAge = cleanupMaxAgeMs;

        tickLog.values().forEach(worldMap -> {
            worldMap.entrySet().removeIf(entry -> {
                long lastTime = entry.getValue();
                return (currentTime - lastTime) > maxAge;
            });
        });

        // Remove empty world maps
        tickLog.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void shutdown() {
        tickLog.clear();
    }
}
