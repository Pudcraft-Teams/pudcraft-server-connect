package org.pudcraft.pudcraftServerConnect.config;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {
    // API
    private String baseUrl;
    private String serverId;
    private String apiKey;

    // WebSocket
    private boolean websocketEnabled;
    private int reconnectDelaySeconds;
    private int maxReconnectDelaySeconds;

    // Whitelist
    private String whitelistMode; // "native" or "plugin"

    // Sync
    private int pollIntervalSeconds;
    private boolean handshakeOnStartup;

    // Status
    private int reportIntervalSeconds;
    private boolean reportTps;
    private boolean reportMemory;

    // Update
    private boolean updateEnabled;
    private int updateCheckIntervalHours;

    // Language
    private String language;

    public void load(FileConfiguration config) {
        this.baseUrl = config.getString("api.base-url", "https://servers.pudcraft.top");
        this.serverId = config.getString("api.server-id", "");
        this.apiKey = config.getString("api.api-key", "");

        this.websocketEnabled = config.getBoolean("websocket.enabled", true);
        this.reconnectDelaySeconds = config.getInt("websocket.reconnect-delay-seconds", 5);
        this.maxReconnectDelaySeconds = config.getInt("websocket.max-reconnect-delay-seconds", 300);

        this.whitelistMode = config.getString("whitelist.mode", "native");

        this.pollIntervalSeconds = config.getInt("sync.poll-interval-seconds", 300);
        this.handshakeOnStartup = config.getBoolean("sync.handshake-on-startup", true);

        this.reportIntervalSeconds = config.getInt("status.report-interval-seconds", 60);
        this.reportTps = config.getBoolean("status.report-tps", true);
        this.reportMemory = config.getBoolean("status.report-memory", true);

        this.updateEnabled = config.getBoolean("update.enabled", true);
        this.updateCheckIntervalHours = config.getInt("update.check-interval-hours", 24);

        this.language = config.getString("language", "zh_CN");
    }

    public boolean isConfigured() {
        return serverId != null && !serverId.isEmpty()
            && apiKey != null && !apiKey.isEmpty();
    }

    // Getters for all fields
    public String getBaseUrl() { return baseUrl; }
    public String getServerId() { return serverId; }
    public String getApiKey() { return apiKey; }
    public boolean isWebsocketEnabled() { return websocketEnabled; }
    public int getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public int getMaxReconnectDelaySeconds() { return maxReconnectDelaySeconds; }
    public String getWhitelistMode() { return whitelistMode; }
    public int getPollIntervalSeconds() { return pollIntervalSeconds; }
    public boolean isHandshakeOnStartup() { return handshakeOnStartup; }
    public int getReportIntervalSeconds() { return reportIntervalSeconds; }
    public boolean isReportTps() { return reportTps; }
    public boolean isReportMemory() { return reportMemory; }
    public boolean isUpdateEnabled() { return updateEnabled; }
    public int getUpdateCheckIntervalHours() { return updateCheckIntervalHours; }
    public String getLanguage() { return language; }
}
