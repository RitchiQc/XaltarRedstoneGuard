package com.xaltar.redstoneguard;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Observer;
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

        long currentTime = System.currentTimeMillis();
        String key = block.getWorld().getName() + ":"
                + block.getX() + ":"
                + block.getY() + ":"
                + block.getZ();

        Long lastTime = observerTickLog.get(key);
        long thresholdTicks = plugin.getConfig().getLong("threshold-ticks", 2);
        long thresholdMs = thresholdTicks * 50L;

        boolean throttle = plugin.getConfig().getBoolean("throttle-observer", false);
        boolean cancel = plugin.getConfig().getBoolean("cancel-observer", true);

        if (lastTime != null) {
            long timeDifference = currentTime - lastTime;

            // throttle: allow pulses at exactly threshold ticks, block only if faster
            // cancel: block if at or below threshold ticks
            boolean isViolation = throttle ? (timeDifference < thresholdMs) : (timeDifference <= thresholdMs);

            if (isViolation) {
                handleViolation(event, block);
                if (!throttle) {
                    // cancel mode: log the current time so the next pulse is evaluated from now
                    observerTickLog.put(key, currentTime);
                }
                return;
            }
        }

        // Log current time for next check
        observerTickLog.put(key, currentTime);
    }

    private void handleViolation(BlockRedstoneEvent event, Block block) {
        // Cancel the redstone signal immediately
        if (plugin.getConfig().getBoolean("cancel-observer", true)
                || plugin.getConfig().getBoolean("throttle-observer", false)) {
            event.setNewCurrent(0);
            resetObserver(block);
        }

        // Remove the observer block without dropping an item
        if (plugin.getConfig().getBoolean("break-observer", false)) {
            if (block.getType() == Material.OBSERVER) {
                block.setType(Material.AIR);
            }
        }
    }

    /**
     * Resets an observer block by re-applying its BlockData, which clears
     * its internal pulse state and effectively cancels the current output.
     * Called synchronously from the event handler (already on the correct regional thread).
     */
    private void resetObserver(Block block) {
        if (block.getType() == Material.OBSERVER) {
            BlockData data = block.getBlockData();
            if (data instanceof Observer) {
                // Re-apply the same BlockData to force the server to reset
                // the observer's internal pulse state without changing orientation
                block.setBlockData(data, false);
            }
        }
    }

    /**
     * Removes stale entries to prevent memory leaks.
     * Safe to call from any thread thanks to ConcurrentHashMap and System.currentTimeMillis().
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        long thresholdTicks = plugin.getConfig().getLong("threshold-ticks", 2);
        long maxAgeMs = (thresholdTicks + 100) * 50L; // Allow a small buffer beyond the threshold

        observerTickLog.entrySet().removeIf(entry -> {
            long lastTime = entry.getValue();
            return (currentTime - lastTime) > maxAgeMs;
        });
    }

    public void shutdown() {
        observerTickLog.clear();
    }
}
