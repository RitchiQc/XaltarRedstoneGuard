package com.xaltar.redstoneguard;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.TimeUnit;

public class XaltarRedstoneGuard extends JavaPlugin {

    private RedstoneLimiter redstoneLimiter;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.redstoneLimiter = new RedstoneLimiter(this);
        Bukkit.getPluginManager().registerEvents(redstoneLimiter, this);

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
        getLogger().info("XaltarRedstoneGuard disabled!");
    }
}
