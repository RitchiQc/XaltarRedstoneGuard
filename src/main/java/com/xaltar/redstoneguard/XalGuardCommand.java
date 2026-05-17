package com.xaltar.redstoneguard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class XalGuardCommand implements CommandExecutor, TabCompleter {

    private final XaltarRedstoneGuard plugin;
    private final LimitMenu limitMenu;
    private final Map<UUID, Long> lastLimitUsage = new HashMap<>();

    public XalGuardCommand(XaltarRedstoneGuard plugin, LimitMenu limitMenu) {
        this.plugin = plugin;
        this.limitMenu = limitMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "limit" -> handleLimit(sender);
            case "reload" -> handleReload(sender);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void handleLimit(CommandSender sender) {
        String requiredPermission = plugin.getConfig().getString("command.limit.permission", "xalguard.limit");
        if (!sender.hasPermission(requiredPermission)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Tu n'as pas la permission d'utiliser cette commande."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Cette commande doit être utilisée par un joueur."));
            return;
        }

        String bypassPermission = plugin.getConfig().getString("command.limit.bypass-permission", "xalguard.limit.bypass");
        boolean hasBypass = sender.hasPermission(bypassPermission);

        if (!hasBypass) {
            int cooldownSeconds = plugin.getConfig().getInt("command.limit.cooldown-seconds", 30);
            UUID playerId = player.getUniqueId();
            long now = System.currentTimeMillis();

            if (lastLimitUsage.containsKey(playerId)) {
                long lastUsed = lastLimitUsage.get(playerId);
                long elapsedSeconds = (now - lastUsed) / 1000;

                if (elapsedSeconds < cooldownSeconds) {
                    long remaining = cooldownSeconds - elapsedSeconds;
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(
                            "<red>Tu dois attendre <yellow>" + remaining + "</yellow> seconde(s) avant de réutiliser cette commande."
                    ));
                    return;
                }
            }

            lastLimitUsage.put(playerId, now);
        }
        limitMenu.open(player);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("xalguard.reload")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Tu n'as pas la permission d'utiliser cette commande."));
            return;
        }

        plugin.reloadConfig();
        plugin.getBlockTracker().reloadLimits();
        plugin.getBlockTracker().rescanAllLoadedChunks();
        plugin.getRedstoneLimiter().reloadLimitedMaterials();

        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Configuration de XaltarRedstoneGuard rechargée avec succès !"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: <gray>/xalguard <limit|reload>"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            String requiredPermission = plugin.getConfig().getString("command.limit.permission", "xalguard.limit");
            if (sender.hasPermission(requiredPermission)) {
                suggestions.add("limit");
            }
            if (sender.hasPermission("xalguard.reload")) {
                suggestions.add("reload");
            }

            String partial = args[0].toLowerCase();
            suggestions.removeIf(s -> !s.toLowerCase().startsWith(partial));
        }

        return suggestions;
    }
}
