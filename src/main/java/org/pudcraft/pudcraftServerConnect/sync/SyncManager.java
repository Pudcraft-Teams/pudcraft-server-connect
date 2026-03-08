package org.pudcraft.pudcraftServerConnect.sync;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;
import org.pudcraft.pudcraftServerConnect.network.PudcraftWebSocketClient;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SyncManager {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final WhitelistManager whitelistManager;
    private final ConfigManager configManager;
    private final Logger logger;
    private PudcraftWebSocketClient wsClient;
    private SyncTask syncTask;

    public SyncManager(PudcraftServerConnect plugin, ApiClient apiClient,
                       WhitelistManager whitelistManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.whitelistManager = whitelistManager;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Perform initial handshake: full whitelist sync + start WS + start poll task.
     */
    public void start() {
        if (configManager.getPluginConfig().isHandshakeOnStartup()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::handshake);
        }
        startPollTask();
    }

    public void handshake() {
        logger.info("Performing handshake with community platform...");
        ApiResponse response = apiClient.post("/api/servers/{id}/sync/handshake", "{}");

        if (!response.isSuccess()) {
            logger.warning("Handshake failed: " + response.getError());
            return;
        }

        try {
            JsonObject json = response.getJsonObject();

            // 1. Full whitelist sync
            JsonArray whitelistArray = json.getAsJsonArray("whitelist");
            List<String> whitelist = new ArrayList<>();
            for (JsonElement el : whitelistArray) {
                whitelist.add(el.getAsString());
            }

            // Run whitelist update on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                whitelistManager.setWhitelist(whitelist);
                logger.info("Handshake: synced " + whitelist.size() + " whitelist entries");
            });

            // 2. Process pending syncs
            JsonArray pendingSyncs = json.getAsJsonArray("pendingSyncs");
            processSyncArray(pendingSyncs);

            // 3. Connect WebSocket
            if (configManager.getPluginConfig().isWebsocketEnabled() && json.has("wsUrl")) {
                String wsUrl = json.get("wsUrl").getAsString();
                connectWebSocket(wsUrl);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse handshake response: " + e.getMessage());
        }
    }

    public void processSync(String syncId, String username, String action) {
        // Execute whitelist operation on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            boolean success;
            if ("add".equals(action)) {
                success = whitelistManager.addPlayer(username);
            } else if ("remove".equals(action)) {
                success = whitelistManager.removePlayer(username);
            } else {
                logger.warning("Unknown sync action: " + action);
                return;
            }

            // ACK async
            if (success) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> ack(syncId));
            }
        });
    }

    private void ack(String syncId) {
        ApiResponse response = apiClient.postEmpty("/api/sync/" + syncId + "/ack");
        if (response.isSuccess()) {
            logger.fine("ACK sent for sync " + syncId);
        } else {
            logger.warning("ACK failed for sync " + syncId + ": " + response.getError());
        }
    }

    public void pollPending() {
        ApiResponse response = apiClient.get("/api/servers/{id}/sync/pending");
        if (!response.isSuccess()) {
            logger.warning("Failed to fetch pending syncs: " + response.getError());
            return;
        }

        try {
            JsonObject json = response.getJsonObject();
            JsonArray pendingSyncs = json.getAsJsonArray("pendingSyncs");
            processSyncArray(pendingSyncs);
        } catch (Exception e) {
            logger.warning("Failed to parse pending syncs: " + e.getMessage());
        }
    }

    private void processSyncArray(JsonArray syncs) {
        if (syncs == null || syncs.isEmpty()) return;
        for (JsonElement el : syncs) {
            JsonObject sync = el.getAsJsonObject();
            String syncId = sync.get("id").getAsString();
            String action = sync.get("action").getAsString();
            JsonElement mcUsernameEl = sync.get("mcUsername");
            if (mcUsernameEl == null || mcUsernameEl.isJsonNull()) {
                logger.warning("Sync " + syncId + " has no mcUsername, skipping");
                continue;
            }
            String username = mcUsernameEl.getAsString();
            processSync(syncId, username, action);
        }
    }

    public void onWebSocketMessage(String message) {
        try {
            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
            String serverId = json.has("serverId") ? json.get("serverId").getAsString() : null;

            // The WS message from Redis pub/sub only contains serverId as a notification
            // We need to fetch pending syncs to get the actual data
            if (serverId != null) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, this::pollPending);
            }
        } catch (Exception e) {
            logger.warning("Failed to parse WebSocket message: " + e.getMessage());
        }
    }

    private void connectWebSocket(String wsUrl) {
        if (wsClient != null) {
            wsClient.shutdown();
        }

        try {
            String url = wsUrl + "/ws?serverId=" + apiClient.getServerId()
                + "&token=" + apiClient.getApiKey();
            URI uri = URI.create(url);

            wsClient = new PudcraftWebSocketClient(
                uri, logger, this::onWebSocketMessage,
                configManager.getPluginConfig().getReconnectDelaySeconds(),
                configManager.getPluginConfig().getMaxReconnectDelaySeconds()
            );
            wsClient.connect();
        } catch (Exception e) {
            logger.warning("Failed to connect WebSocket: " + e.getMessage());
        }
    }

    private void startPollTask() {
        int intervalTicks = configManager.getPluginConfig().getPollIntervalSeconds() * 20;
        syncTask = new SyncTask(this);
        syncTask.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        if (wsClient != null) {
            wsClient.shutdown();
        }
    }

    public boolean isWebSocketConnected() {
        return wsClient != null && wsClient.isConnectedAndOpen();
    }
}
