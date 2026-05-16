package com.xaltar.redstoneguard;

import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ItemLimitListener implements Listener {

    private final XaltarRedstoneGuard plugin;
    private final ChunkItemTracker itemTracker;

    public ItemLimitListener(XaltarRedstoneGuard plugin, ChunkItemTracker itemTracker) {
        this.plugin = plugin;
        this.itemTracker = itemTracker;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        Item item = event.getEntity();
        ChunkKey key = ChunkKey.fromLocation(item.getLocation());
        itemTracker.addItem(key, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        Item item = event.getEntity();
        ChunkKey key = ChunkKey.fromLocation(item.getLocation());
        itemTracker.removeItem(key, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        // The entity being merged (removed) should be removed from tracking
        Item item = event.getEntity();
        ChunkKey key = ChunkKey.fromLocation(item.getLocation());
        itemTracker.removeItem(key, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        Item item = event.getItem();
        ChunkKey key = ChunkKey.fromLocation(item.getLocation());
        itemTracker.removeItem(key, item);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("item-limits.enabled", true)) {
            return;
        }

        ChunkKey key = ChunkKey.fromChunk(event.getChunk());
        itemTracker.removeChunk(key);
    }
}
