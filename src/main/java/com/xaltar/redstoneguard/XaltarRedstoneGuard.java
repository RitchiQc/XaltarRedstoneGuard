package com.xaltar.redstoneguard;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class XaltarRedstoneGuard extends JavaPlugin {

    private RedstoneLimiter redstoneLimiter;
    private ChunkBlockTracker blockTracker;
    private ChunkItemTracker itemTracker;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.redstoneLimiter = new RedstoneLimiter(this);
        Bukkit.getPluginManager().registerEvents(redstoneLimiter, this);

        this.blockTracker = new ChunkBlockTracker(this);
        Bukkit.getPluginManager().registerEvents(new BlockLimitListener(this, blockTracker), this);

        this.itemTracker = new ChunkItemTracker(this);
        Bukkit.getPluginManager().registerEvents(new ItemLimitListener(this, itemTracker), this);

        long cleanupMinutes = getConfig().getLong("cleanup-interval-minutes", 5);

        // Folia async scheduler for periodic cleanup
        Bukkit.getAsyncScheduler().runAtFixedRate(
                this,
                task -> redstoneLimiter.cleanup(),
                cleanupMinutes,
                cleanupMinutes,
                TimeUnit.MINUTES
        );

        getLogger().info("XaltarRedstoneGuard enabled!");
    }

    @Override
    public void onDisable() {
        if (redstoneLimiter != null) {
            redstoneLimiter.shutdown();
        }
        if (blockTracker != null) {
            blockTracker.shutdown();
        }
        if (itemTracker != null) {
            itemTracker.shutdown();
        }
        getLogger().info("XaltarRedstoneGuard disabled!");
    }
}
