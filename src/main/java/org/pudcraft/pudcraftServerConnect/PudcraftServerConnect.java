package org.pudcraft.pudcraftServerConnect;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.pudcraft.pudcraftServerConnect.command.MainCommand;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.status.StatusReporter;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.verify.MotdVerifyManager;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

public final class PudcraftServerConnect extends JavaPlugin {
    private ConfigManager configManager;
    private SyncManager syncManager;
    private StatusReporter statusReporter;

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
    public void reload() {
        shutdownServices();
        configManager.reload();
        startServices();
    }

    private void startServices() {
        if (!configManager.getPluginConfig().isConfigured()) {
            getLogger().warning(configManager.getMessageManager()
                .getRaw("config.missing-api-config"));
            getLogger().warning("Plugin will not connect until configured. Edit config.yml and run /pudcraft reload");
            registerCommand(new MainCommand(this, configManager, null, null, null));
            return;
        }

        // Network
        ApiClient apiClient = new ApiClient(configManager.getPluginConfig(), getLogger());

        // Whitelist
        WhitelistManager whitelistManager = new WhitelistManager(this, configManager);

        // Sync
        syncManager = new SyncManager(this, apiClient, whitelistManager, configManager);
        syncManager.start();

        // Status
        statusReporter = new StatusReporter(this, apiClient, configManager);
        statusReporter.start();

        // Verify
        MotdVerifyManager verifyManager = new MotdVerifyManager(this, apiClient, configManager.getMessageManager());

        // Commands
        registerCommand(new MainCommand(this, configManager, syncManager, whitelistManager, verifyManager));

        getLogger().info("PudCraft Server Connect enabled successfully");
    }

    private void shutdownServices() {
        if (statusReporter != null) {
            statusReporter.reportOffline();
            statusReporter.shutdown();
            statusReporter = null;
        }
        if (syncManager != null) {
            syncManager.shutdown();
            syncManager = null;
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
