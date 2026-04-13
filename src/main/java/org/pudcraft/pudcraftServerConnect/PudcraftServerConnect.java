package org.pudcraft.pudcraftServerConnect;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.pudcraft.pudcraftServerConnect.command.MainCommand;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.status.StatusReporter;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.update.UpdateChecker;
import org.pudcraft.pudcraftServerConnect.verify.MotdVerifyManager;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.util.concurrent.CompletableFuture;

public final class PudcraftServerConnect extends JavaPlugin {
    private ConfigManager configManager;
    private SyncManager syncManager;
    private WhitelistManager whitelistManager;
    private StatusReporter statusReporter;
    private UpdateChecker updateChecker;
    private CompletableFuture<Void> reloadInFlight;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();
        startServices();
    }

    @Override
    public void onDisable() {
        shutdownServices();
        getLogger().info("PudCraft Server Connect disabled");
    }

    /**
     * Full reload: shutdown existing services, reload config, restart everything.
     */
    public synchronized CompletableFuture<Void> reload() {
        if (reloadInFlight != null && !reloadInFlight.isDone()) {
            return reloadInFlight;
        }

        CompletableFuture<Void> reloadFuture = shutdownServices()
            .thenCompose(ignored -> runOnMainThread(() -> {
                configManager.reload();
                startServices();
            }));
        reloadInFlight = reloadFuture;
        reloadFuture.whenComplete((ignored, error) -> clearReloadInFlight(reloadFuture));
        return reloadFuture;
    }

    private void startServices() {
        // Update checker (works regardless of API config)
        updateChecker = new UpdateChecker(this, configManager);
        updateChecker.start();

        if (!configManager.getPluginConfig().isConfigured()) {
            getLogger().warning(configManager.getMessageManager()
                .getRaw("config.missing-api-config"));
            getLogger().warning("Plugin will not connect until configured. Edit config.yml and run /pudcraft reload");
            registerCommand(new MainCommand(this, configManager, null, null, null, updateChecker));
            return;
        }

        // Network
        ApiClient apiClient = new ApiClient(configManager.getPluginConfig(), getLogger());

        // Whitelist
        whitelistManager = new WhitelistManager(this, configManager);

        // Sync
        syncManager = new SyncManager(this, apiClient, whitelistManager, configManager);
        syncManager.start();

        // Status
        statusReporter = new StatusReporter(this, apiClient, configManager);
        statusReporter.start();

        // Verify
        MotdVerifyManager verifyManager = new MotdVerifyManager(this, apiClient, configManager.getMessageManager());

        // Commands
        registerCommand(new MainCommand(this, configManager, syncManager, whitelistManager, verifyManager, updateChecker));

        getLogger().info("PudCraft Server Connect enabled successfully");
    }

    private CompletableFuture<Void> shutdownServices() {
        CompletableFuture<Void> offlineReport = CompletableFuture.completedFuture(null);
        if (updateChecker != null) {
            updateChecker.shutdown();
            updateChecker = null;
        }
        if (statusReporter != null) {
            offlineReport = statusReporter.reportOffline()
                .handle((response, error) -> null);
            statusReporter.shutdown();
            statusReporter = null;
        }
        if (syncManager != null) {
            syncManager.shutdown();
            syncManager = null;
        }
        if (whitelistManager != null) {
            whitelistManager.shutdown();
            whitelistManager = null;
        }
        return offlineReport;
    }

    private CompletableFuture<Void> runOnMainThread(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            getServer().getScheduler().runTask(this, () -> {
                try {
                    task.run();
                    future.complete(null);
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    private synchronized void clearReloadInFlight(CompletableFuture<Void> reloadFuture) {
        if (reloadInFlight == reloadFuture) {
            reloadInFlight = null;
        }
    }

    private void registerCommand(MainCommand cmd) {
        PluginCommand command = getCommand("pudcraft");
        if (command != null) {
            command.setExecutor(cmd);
            command.setTabCompleter(cmd);
        }
    }
}
