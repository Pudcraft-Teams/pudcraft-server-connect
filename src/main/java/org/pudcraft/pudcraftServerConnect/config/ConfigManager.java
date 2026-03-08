package org.pudcraft.pudcraftServerConnect.config;

import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;

public class ConfigManager {
    private final PudcraftServerConnect plugin;
    private final PluginConfig pluginConfig;
    private final MessageManager messageManager;

    public ConfigManager(PudcraftServerConnect plugin) {
        this.plugin = plugin;
        this.pluginConfig = new PluginConfig();
        this.messageManager = new MessageManager(plugin);
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        pluginConfig.load(plugin.getConfig());
        messageManager.load(pluginConfig.getLanguage());
    }

    public void reload() {
        load();
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }
}
