package com.xaltar.redstoneguard;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class XalGuardCommand implements CommandExecutor {

    private final XaltarRedstoneGuard plugin;
    private final LimitMenu limitMenu;

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
        if (!sender.hasPermission("xalguard.limit")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Tu n'as pas la permission d'utiliser cette commande."));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Cette commande doit être utilisée par un joueur."));
            return;
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

        sender.sendMessage(MiniMessage.miniMessage().deserialize("<green>Configuration de XaltarRedstoneGuard rechargée avec succès !"));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize("<yellow>Usage: <gray>/xalguard <limit|reload>"));
    }
}
