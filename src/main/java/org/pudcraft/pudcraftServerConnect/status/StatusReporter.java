package org.pudcraft.pudcraftServerConnect.status;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.scheduler.BukkitTask;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;

import java.util.logging.Logger;

public class StatusReporter {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final ConfigManager configManager;
    private final Logger logger;
    private BukkitTask reportTask;

    public StatusReporter(PudcraftServerConnect plugin, ApiClient apiClient,
                          ConfigManager configManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    public void start() {
        int intervalTicks = configManager.getPluginConfig().getReportIntervalSeconds() * 20;
        reportTask = plugin.getServer().getScheduler()
            .runTaskTimer(plugin, () -> report(true), 100L, intervalTicks); // 5s initial delay
    }

    public void report(boolean online) {
        String jsonBody = buildStatusPayload(online);
        logger.info("Status report body: " + jsonBody);
        apiClient.postAsync("/api/servers/{id}/status/report", jsonBody)
            .thenAccept(this::logIfFailed);
    }

    private String buildStatusPayload(boolean online) {
        Server server = plugin.getServer();
        JsonObject body = new JsonObject();
        body.addProperty("online", online);
        body.addProperty("playerCount", server.getOnlinePlayers().size());
        body.addProperty("maxPlayers", server.getMaxPlayers());
        body.addProperty("version", server.getVersion());

        if (configManager.getPluginConfig().isReportTps()) {
            try {
                // Paper API only – use reflection to avoid compile-time dependency
                double[] tps = (double[]) Bukkit.class.getMethod("getTPS").invoke(null);
                body.addProperty("tps", Math.round(tps[0] * 100.0) / 100.0);
            } catch (Exception ignored) {
                // Spigot without Paper API
            }
        }

        if (configManager.getPluginConfig().isReportMemory()) {
            Runtime runtime = Runtime.getRuntime();
            body.addProperty("memoryUsed", runtime.totalMemory() - runtime.freeMemory());
            body.addProperty("memoryMax", runtime.maxMemory());
        }

        return body.toString();
    }

    private void logIfFailed(ApiResponse response) {
        if (!response.isSuccess()) {
            logger.warning("Status report failed (HTTP " + response.getStatusCode() + "): " + response.getBody());
        }
    }

    public void reportOffline() {
        report(false);
    }

    public void shutdown() {
        if (reportTask != null) {
            reportTask.cancel();
        }
    }
}
