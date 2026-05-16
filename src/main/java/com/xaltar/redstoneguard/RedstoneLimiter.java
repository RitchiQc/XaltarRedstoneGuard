package com.xaltar.redstoneguard;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

import java.util.concurrent.ConcurrentHashMap;

public class RedstoneLimiter implements Listener {

    private final XaltarRedstoneGuard plugin;
    private final ConcurrentHashMap<String, Long> observerTickLog = new ConcurrentHashMap<>();

    public RedstoneLimiter(XaltarRedstoneGuard plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        // Master switch
        if (!plugin.getConfig().getBoolean("limit-observer", true)) {
            return;
        }

        Block block = event.getBlock();

        // Only handle observers
        if (block.getType() != Material.OBSERVER) {
            return;
        }

        // Only handle rising edge (when observer starts pulsing)
        if (event.getNewCurrent() <= 0) {
            return;
        }

        long currentTick = Bukkit.getCurrentTick();
        String key = block.getWorld().getName() + ":"
                + block.getX() + ":"
                + block.getY() + ":"
                + block.getZ();

        Long lastTick = observerTickLog.get(key);
        long threshold = plugin.getConfig().getLong("threshold-ticks", 2);

        if (lastTick != null) {
            long tickDifference = currentTick - lastTick;

            if (tickDifference <= threshold) {
                // Observer is pulsing too fast
                handleViolation(event, block);
                return;
            }
        }

        // Log current tick for next check
        observerTickLog.put(key, currentTick);
    }

    private void handleViolation(BlockRedstoneEvent event, Block block) {
        // Cancel the redstone signal by forcing current to 0
        if (plugin.getConfig().getBoolean("cancel-observer", true)) {
            event.setNewCurrent(0);
        }

        // Remove the observer block without dropping an item
        if (plugin.getConfig().getBoolean("break-observer", false)) {
            // Schedule block removal on the block's regional scheduler to ensure thread-safety under Folia
            Bukkit.getRegionScheduler().execute(plugin, block.getLocation(), () -> {
                if (block.getType() == Material.OBSERVER) {
                    block.setType(Material.AIR);
                }
            });
        }
    }

    /**
     * Removes stale entries to prevent memory leaks.
     * Safe to call from any thread thanks to ConcurrentHashMap.
     */
    public void cleanup() {
        long currentTick = Bukkit.getCurrentTick();
        long threshold = plugin.getConfig().getLong("threshold-ticks", 2);
        long maxAge = threshold + 100; // Allow a small buffer beyond the threshold

        observerTickLog.entrySet().removeIf(entry -> {
            long lastTick = entry.getValue();
            return (currentTick - lastTick) > maxAge;
        });
    }

    public void shutdown() {
        observerTickLog.clear();
    }
}
