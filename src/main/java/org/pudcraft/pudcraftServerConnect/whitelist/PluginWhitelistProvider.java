package org.pudcraft.pudcraftServerConnect.whitelist;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class PluginWhitelistProvider implements WhitelistProvider, Listener {
    private final PudcraftServerConnect plugin;
    private final Logger logger;
    private final MessageManager messageManager;
    private final File whitelistFile;
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();

    public PluginWhitelistProvider(PudcraftServerConnect plugin, MessageManager messageManager) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.messageManager = messageManager;
        this.whitelistFile = new File(plugin.getDataFolder(), "whitelist.json");
        loadFromFile();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(AsyncPlayerPreLoginEvent event) {
        if (!whitelist.contains(event.getName().toLowerCase())) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                messageManager.getRaw("whitelist.not-whitelisted"));
        }
    }

    @Override
    public boolean addPlayer(String username) {
        boolean added = whitelist.add(username.toLowerCase());
        if (added) {
            saveToFile();
            logger.info("Added " + username + " to plugin whitelist");
        }
        return added;
    }

    @Override
    public boolean removePlayer(String username) {
        boolean removed = whitelist.remove(username.toLowerCase());
        if (removed) {
            saveToFile();
            logger.info("Removed " + username + " from plugin whitelist");
        }
        return removed;
    }

    @Override
    public boolean isWhitelisted(String username) {
        return whitelist.contains(username.toLowerCase());
    }

    @Override
    public List<String> getWhitelistedPlayers() {
        return new ArrayList<>(whitelist);
    }

    public void setWhitelist(List<String> players) {
        whitelist.clear();
        players.forEach(p -> whitelist.add(p.toLowerCase()));
        saveToFile();
    }

    private void loadFromFile() {
        if (!whitelistFile.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(whitelistFile), StandardCharsets.UTF_8)) {
            Type type = new TypeToken<List<String>>(){}.getType();
            List<String> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                loaded.forEach(p -> whitelist.add(p.toLowerCase()));
            }
            logger.info("Loaded " + whitelist.size() + " players from plugin whitelist");
        } catch (Exception e) {
            logger.warning("Failed to load whitelist.json: " + e.getMessage());
        }
    }

    private void saveToFile() {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(whitelistFile), StandardCharsets.UTF_8)) {
            gson.toJson(new ArrayList<>(whitelist), writer);
        } catch (Exception e) {
            logger.warning("Failed to save whitelist.json: " + e.getMessage());
        }
    }
}
