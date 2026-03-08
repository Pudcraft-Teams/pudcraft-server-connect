package org.pudcraft.pudcraftServerConnect;

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
    private ApiClient apiClient;
    private WhitelistManager whitelistManager;
    private SyncManager syncManager;
    private StatusReporter statusReporter;
    private MotdVerifyManager verifyManager;

    @Override
    public void onEnable() {
        // 1. Config
        configManager = new ConfigManager(this);
        configManager.load();

        // 2. Check configuration
        if (!configManager.getPluginConfig().isConfigured()) {
            getLogger().warning(configManager.getMessageManager()
                .getRaw("config.missing-api-config"));
            getLogger().warning("Plugin will not connect until configured. Edit config.yml and run /pudcraft reload");
            registerCommandsOnly();
            return;
        }

        // 3. Network
        apiClient = new ApiClient(configManager.getPluginConfig(), getLogger());

        // 4. Whitelist
        whitelistManager = new WhitelistManager(this, configManager);

        // 5. Sync
        syncManager = new SyncManager(this, apiClient, whitelistManager, configManager);
        syncManager.start();

        // 6. Status
        statusReporter = new StatusReporter(this, apiClient, configManager);
        statusReporter.start();

        // 7. Verify
        verifyManager = new MotdVerifyManager(this, apiClient, configManager.getMessageManager());

        // 8. Commands
        registerCommands();

        getLogger().info("PudCraft Server Connect enabled successfully");
    }

    @Override
    public void onDisable() {
        // Report offline status
        if (statusReporter != null) {
            statusReporter.reportOffline();
            statusReporter.shutdown();
        }

        // Shutdown sync and WebSocket
        if (syncManager != null) {
            syncManager.shutdown();
        }

        getLogger().info("PudCraft Server Connect disabled");
    }

    private void registerCommands() {
        MainCommand cmd = new MainCommand(this, configManager, syncManager,
            whitelistManager, verifyManager);
        getCommand("pudcraft").setExecutor(cmd);
        getCommand("pudcraft").setTabCompleter(cmd);
    }

    private void registerCommandsOnly() {
        // Register commands that work without API connection (reload only)
        MainCommand cmd = new MainCommand(this, configManager, null, null, null);
        getCommand("pudcraft").setExecutor(cmd);
        getCommand("pudcraft").setTabCompleter(cmd);
    }
}
