package org.pudcraft.pudcraftServerConnect.status;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
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
    private BukkitRunnable reportTask;

    public StatusReporter(PudcraftServerConnect plugin, ApiClient apiClient,
                          ConfigManager configManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
    }

    public void start() {
        int intervalTicks = configManager.getPluginConfig().getReportIntervalSeconds() * 20;
        reportTask = new BukkitRunnable() {
            @Override
            public void run() {
                report(true);
            }
        };
        reportTask.runTaskTimerAsynchronously(plugin, 100L, intervalTicks); // 5s initial delay
    }

    public void report(boolean online) {
        JsonObject body = new JsonObject();
        body.addProperty("online", online);
        body.addProperty("playerCount", Bukkit.getOnlinePlayers().size());
        body.addProperty("maxPlayers", Bukkit.getMaxPlayers());
        body.addProperty("version", Bukkit.getVersion());

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

        ApiResponse response = apiClient.post("/api/servers/{id}/status/report", body.toString());
        if (!response.isSuccess()) {
            logger.warning("Status report failed: " + response.getError());
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
