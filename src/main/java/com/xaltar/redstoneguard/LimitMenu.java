package com.xaltar.redstoneguard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LimitMenu implements Listener {

    private final XaltarRedstoneGuard plugin;
    private final ChunkBlockTracker blockTracker;
    private static final String INVENTORY_TITLE = "<dark_gray>Limites du Chunk";

    public LimitMenu(XaltarRedstoneGuard plugin, ChunkBlockTracker blockTracker) {
        this.plugin = plugin;
        this.blockTracker = blockTracker;
    }

    public void open(Player player) {
        ChunkKey key = ChunkKey.fromLocation(player.getLocation());
        Map<Material, Integer> limits = blockTracker.getAllLimits();
        Map<Material, Integer> counts = blockTracker.getAllCounts(key);

        int size = Math.max(9, ((limits.size() + 8) / 9) * 9);
        Inventory inventory = Bukkit.createInventory(null, size, MiniMessage.miniMessage().deserialize(INVENTORY_TITLE));

        int slot = 0;
        for (Map.Entry<Material, Integer> entry : limits.entrySet()) {
            Material material = entry.getKey();
            int limit = entry.getValue();
            int count = counts.getOrDefault(material, 0);

            ItemStack item = createDisplayItem(material, count, limit);
            inventory.setItem(slot++, item);
        }

        player.openInventory(inventory);
    }

    private ItemStack createDisplayItem(Material material, int count, int limit) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        String displayName = formatMaterialName(material);
        meta.displayName(MiniMessage.miniMessage().deserialize("<yellow>" + displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(MiniMessage.miniMessage().deserialize("<gray>Actuel: <yellow>" + count + " <gray>/ <yellow>" + limit));
        lore.add(MiniMessage.miniMessage().deserialize("<gray>" + buildProgressBar(count, limit)));

        double percentage = limit > 0 ? ((double) count / limit) * 100 : 0;
        String color = percentage >= 100 ? "<red>" : percentage >= 75 ? "<gold>" : "<green>";
        lore.add(MiniMessage.miniMessage().deserialize("<gray>Pourcentage: " + color + String.format("%.1f", percentage) + "%"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildProgressBar(int count, int limit) {
        int barLength = 20;
        int filled = limit > 0 ? Math.min(barLength, (count * barLength) / limit) : 0;
        int empty = barLength - filled;

        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < filled; i++) {
            bar.append("<green>█");
        }
        for (int i = 0; i < empty; i++) {
            bar.append("<dark_gray>█");
        }
        bar.append("<gray>]");
        return bar.toString();
    }

    private String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace("_", " ");
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1))
                        .append(" ");
            }
        }
        return result.toString().trim();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().title().equals(MiniMessage.miniMessage().deserialize(INVENTORY_TITLE))) {
            event.setCancelled(true);
        }
    }
}
