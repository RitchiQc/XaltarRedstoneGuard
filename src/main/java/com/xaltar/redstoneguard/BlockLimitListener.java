package com.xaltar.redstoneguard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class BlockLimitListener implements Listener {

    private final XaltarRedstoneGuard plugin;
    private final ChunkBlockTracker blockTracker;

    public BlockLimitListener(XaltarRedstoneGuard plugin, ChunkBlockTracker blockTracker) {
        this.plugin = plugin;
        this.blockTracker = blockTracker;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!plugin.getConfig().getBoolean("block-limits.enabled", true)) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();

        if (!isLimitedMaterial(material)) {
            return;
        }

        ChunkKey key = ChunkKey.fromBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getZ()
        );

        boolean allowed = blockTracker.tryIncrement(key, material);
        if (!allowed) {
            event.setCancelled(true);

            Player player = event.getPlayer();
            String message = plugin.getConfig().getString("block-limits.message",
                    "<red>Limite de <yellow>%type%</yellow> atteinte dans ce chunk (<yellow>%limit%</yellow> blocs).");

            String typeName = getDisplayName(material);
            int limit = blockTracker.getLimit(material);

            String formatted = message
                    .replace("%type%", typeName)
                    .replace("%limit%", String.valueOf(limit));

            player.sendMessage(MiniMessage.miniMessage().deserialize(formatted));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("block-limits.enabled", true)) {
            return;
        }

        Block block = event.getBlock();
        Material material = block.getType();

        if (!isLimitedMaterial(material)) {
            return;
        }

        ChunkKey key = ChunkKey.fromBlock(
                block.getWorld().getName(),
                block.getX(),
                block.getZ()
        );

        blockTracker.decrement(key, material);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.getConfig().getBoolean("block-limits.enabled", true)) {
            return;
        }

        blockTracker.scanChunk(event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent event) {
        if (!plugin.getConfig().getBoolean("block-limits.enabled", true)) {
            return;
        }

        ChunkKey key = ChunkKey.fromChunk(event.getChunk());
        blockTracker.removeChunk(key);
    }

    private boolean isLimitedMaterial(Material material) {
        for (Material m : blockTracker.getLimitedMaterials()) {
            if (m == material) {
                return true;
            }
        }
        return false;
    }

    private String getDisplayName(Material material) {
        return switch (material) {
            case CRAFTER -> "Crafters";
            case DISPENSER -> "Dispensers";
            case DROPPER -> "Droppers";
            default -> material.name().toLowerCase().replace("_", " ");
        };
    }
}
