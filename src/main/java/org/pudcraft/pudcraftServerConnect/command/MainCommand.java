package org.pudcraft.pudcraftServerConnect.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.Bukkit;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.verify.MotdVerifyManager;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainCommand implements CommandExecutor, TabCompleter {
    private final PudcraftServerConnect plugin;
    private final ConfigManager configManager;
    private final SyncManager syncManager;
    private final WhitelistManager whitelistManager;
    private final MotdVerifyManager verifyManager;

    public MainCommand(PudcraftServerConnect plugin, ConfigManager configManager,
                       SyncManager syncManager, WhitelistManager whitelistManager,
                       MotdVerifyManager verifyManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.syncManager = syncManager;
        this.whitelistManager = whitelistManager;
        this.verifyManager = verifyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageManager msg = configManager.getMessageManager();

        if (args.length == 0) {
            sender.sendMessage(msg.get("command.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("pudcraft.reload")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(configManager.getMessageManager().get("config.reload-success"));
            }
            case "status" -> {
                if (!sender.hasPermission("pudcraft.status")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                if (syncManager == null) {
                    sender.sendMessage(msg.get("config.missing-api-config"));
                    return true;
                }
                showStatus(sender, msg);
            }
            case "verify" -> {
                if (!sender.hasPermission("pudcraft.verify")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                if (verifyManager == null) {
                    sender.sendMessage(msg.get("config.missing-api-config"));
                    return true;
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String result = verifyManager.claim();
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(result));
                });
            }
            case "sync" -> {
                if (!sender.hasPermission("pudcraft.sync")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                if (syncManager == null) {
                    sender.sendMessage(msg.get("config.missing-api-config"));
                    return true;
                }
                sender.sendMessage(msg.get("sync.manual-trigger"));
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> syncManager.pollPending());
            }
            case "whitelist" -> {
                if (!sender.hasPermission("pudcraft.whitelist")) {
                    sender.sendMessage(msg.get("command.no-permission"));
                    return true;
                }
                if (whitelistManager == null) {
                    sender.sendMessage(msg.get("config.missing-api-config"));
                    return true;
                }
                handleWhitelist(sender, args, msg);
            }
            default -> sender.sendMessage(msg.get("command.unknown-subcommand"));
        }

        return true;
    }

    private void showStatus(CommandSender sender, MessageManager msg) {
        sender.sendMessage(msg.getRaw("status.header"));
        sender.sendMessage(msg.get("status.server-id",
            Map.of("id", configManager.getPluginConfig().getServerId())));
        sender.sendMessage(msg.get("status.whitelist-mode",
            Map.of("mode", whitelistManager.getMode())));

        if (syncManager.isWebSocketConnected()) {
            sender.sendMessage(msg.getRaw("status.ws-connected"));
        } else if (!configManager.getPluginConfig().isWebsocketEnabled()) {
            sender.sendMessage(msg.getRaw("status.ws-disabled"));
        } else {
            sender.sendMessage(msg.getRaw("status.ws-disconnected"));
        }
    }

    private void handleWhitelist(CommandSender sender, String[] args, MessageManager msg) {
        if (args.length < 2 || "list".equalsIgnoreCase(args[1])) {
            List<String> players = whitelistManager.getWhitelistedPlayers();
            sender.sendMessage(msg.get("whitelist.list-header",
                Map.of("count", String.valueOf(players.size()))));
            if (players.isEmpty()) {
                sender.sendMessage(msg.getRaw("whitelist.list-empty"));
            } else {
                for (String player : players) {
                    sender.sendMessage(msg.getRaw("whitelist.list-entry").replace("{player}", player));
                }
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("reload", "status", "verify", "sync", "whitelist");
            return subs.stream()
                .filter(s -> s.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        if (args.length == 2 && "whitelist".equalsIgnoreCase(args[0])) {
            return List.of("list").stream()
                .filter(s -> s.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
