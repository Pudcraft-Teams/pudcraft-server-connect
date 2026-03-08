package org.pudcraft.pudcraftServerConnect.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Logger;

public class UpdateChecker {
    private static final String GITHUB_API =
        "https://api.github.com/repos/Pudcraft-Teams/pudcraft-server-connect/releases/latest";

    private final PudcraftServerConnect plugin;
    private final ConfigManager configManager;
    private final Logger logger;
    private final HttpClient httpClient;
    private BukkitRunnable checkTask;

    public UpdateChecker(PudcraftServerConnect plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.logger = plugin.getLogger();
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    public void start() {
        if (!configManager.getPluginConfig().isUpdateEnabled()) {
            return;
        }

        // Check on startup (async, 10s delay)
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> checkAndDownload(null), 200L);

        // Schedule periodic checks
        long intervalTicks = configManager.getPluginConfig().getUpdateCheckIntervalHours() * 72000L;
        checkTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndDownload(null);
            }
        };
        checkTask.runTaskTimerAsynchronously(plugin, intervalTicks, intervalTicks);
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    /**
     * Check for updates and download if available.
     * @param sender CommandSender to notify, or null for console-only logging.
     */
    public void checkAndDownload(CommandSender sender) {
        String currentVersion = plugin.getDescription().getVersion();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GITHUB_API))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "PudCraft-Server-Connect/" + currentVersion)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                String error = "GitHub API returned " + response.statusCode();
                logger.warning("Update check failed: " + error);
                if (sender != null) {
                    notify(sender, "update.check-failed", Map.of("reason", error));
                }
                return;
            }

            JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();
            String tagName = release.get("tag_name").getAsString();
            String latestVersion = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            if (!isNewerVersion(currentVersion, latestVersion)) {
                logger.info("Plugin is up to date (v" + currentVersion + ")");
                if (sender != null) {
                    notify(sender, "update.up-to-date", Map.of("version", currentVersion));
                }
                return;
            }

            logger.info("New version available: v" + latestVersion + " (current: v" + currentVersion + ")");
            if (sender != null) {
                notify(sender, "update.found", Map.of("current", currentVersion, "latest", latestVersion));
            }

            // Find JAR asset
            String downloadUrl = findJarAssetUrl(release);
            if (downloadUrl == null) {
                String error = "No JAR asset found in release";
                logger.warning("Update download failed: " + error);
                if (sender != null) {
                    notify(sender, "update.download-failed", Map.of("reason", error));
                }
                return;
            }

            downloadUpdate(downloadUrl, sender);

        } catch (Exception e) {
            logger.warning("Update check failed: " + e.getMessage());
            if (sender != null) {
                notify(sender, "update.check-failed", Map.of("reason", e.getMessage()));
            }
        }
    }

    private String findJarAssetUrl(JsonObject release) {
        JsonArray assets = release.getAsJsonArray("assets");
        if (assets == null) return null;

        for (JsonElement element : assets) {
            JsonObject asset = element.getAsJsonObject();
            String name = asset.get("name").getAsString();
            if (name.endsWith(".jar")) {
                return asset.get("browser_download_url").getAsString();
            }
        }
        return null;
    }

    private void downloadUpdate(String downloadUrl, CommandSender sender) {
        try {
            if (sender != null) {
                notify(sender, "update.downloading");
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(downloadUrl))
                .header("User-Agent", "PudCraft-Server-Connect")
                .timeout(Duration.ofSeconds(60))
                .GET()
                .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                String error = "Download returned " + response.statusCode();
                logger.warning("Update download failed: " + error);
                if (sender != null) {
                    notify(sender, "update.download-failed", Map.of("reason", error));
                }
                return;
            }

            // Create plugins/update/ directory
            File updateDir = new File(plugin.getDataFolder().getParentFile(), "update");
            if (!updateDir.exists()) {
                updateDir.mkdirs();
            }

            // Save with same filename as current plugin JAR
            File pluginFile = getPluginFile();
            Path targetPath = new File(updateDir, pluginFile.getName()).toPath();

            try (InputStream in = response.body()) {
                Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("Update downloaded to " + targetPath + ". It will be applied on next server restart.");
            if (sender != null) {
                notify(sender, "update.download-success");
            }

        } catch (Exception e) {
            logger.warning("Update download failed: " + e.getMessage());
            if (sender != null) {
                notify(sender, "update.download-failed", Map.of("reason", e.getMessage()));
            }
        }
    }

    @SuppressWarnings("deprecation")
    private File getPluginFile() {
        try {
            Method method = JavaPlugin.class.getDeclaredMethod("getFile");
            method.setAccessible(true);
            return (File) method.invoke(plugin);
        } catch (Exception e) {
            return new File(plugin.getDataFolder().getParentFile(),
                plugin.getDescription().getName() + ".jar");
        }
    }

    /**
     * Compare version strings. Returns true if latestVersion is newer than currentVersion.
     */
    static boolean isNewerVersion(String current, String latest) {
        String cleanCurrent = current.replace("-SNAPSHOT", "");
        String cleanLatest = latest.replace("-SNAPSHOT", "");

        String[] currentParts = cleanCurrent.split("\\.");
        String[] latestParts = cleanLatest.split("\\.");

        int maxLen = Math.max(currentParts.length, latestParts.length);
        for (int i = 0; i < maxLen; i++) {
            int c = i < currentParts.length ? parseIntSafe(currentParts[i]) : 0;
            int l = i < latestParts.length ? parseIntSafe(latestParts[i]) : 0;
            if (l > c) return true;
            if (l < c) return false;
        }

        // Same version numbers: SNAPSHOT is older than release
        return current.contains("-SNAPSHOT") && !latest.contains("-SNAPSHOT");
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void notify(CommandSender sender, String key) {
        Bukkit.getScheduler().runTask(plugin, () ->
            sender.sendMessage(configManager.getMessageManager().get(key)));
    }

    private void notify(CommandSender sender, String key, Map<String, String> placeholders) {
        Bukkit.getScheduler().runTask(plugin, () ->
            sender.sendMessage(configManager.getMessageManager().get(key, placeholders)));
    }
}
