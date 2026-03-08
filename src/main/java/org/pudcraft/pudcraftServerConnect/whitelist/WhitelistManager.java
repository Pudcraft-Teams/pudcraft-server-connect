package org.pudcraft.pudcraftServerConnect.whitelist;

import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;

import java.util.List;
import java.util.logging.Logger;

public class WhitelistManager {
    private final WhitelistProvider provider;
    private final String mode;
    private final Logger logger;

    public WhitelistManager(PudcraftServerConnect plugin, ConfigManager configManager) {
        this.logger = plugin.getLogger();
        this.mode = configManager.getPluginConfig().getWhitelistMode();

        if ("plugin".equalsIgnoreCase(mode)) {
            this.provider = new PluginWhitelistProvider(plugin, configManager.getMessageManager());
            logger.info("Using plugin-managed whitelist mode");
        } else {
            this.provider = new NativeWhitelistProvider(logger);
            logger.info("Using native Bukkit whitelist mode");
        }
    }

    public boolean addPlayer(String username) {
        return provider.addPlayer(username);
    }

    public boolean removePlayer(String username) {
        return provider.removePlayer(username);
    }

    public boolean isWhitelisted(String username) {
        return provider.isWhitelisted(username);
    }

    public List<String> getWhitelistedPlayers() {
        return provider.getWhitelistedPlayers();
    }

    /**
     * Full whitelist replacement (used during handshake).
     * Only supported in plugin mode.
     */
    public void setWhitelist(List<String> players) {
        if (provider instanceof PluginWhitelistProvider pluginProvider) {
            pluginProvider.setWhitelist(players);
        } else {
            // In native mode, add missing players and remove extras
            List<String> current = provider.getWhitelistedPlayers();
            for (String player : players) {
                if (!provider.isWhitelisted(player)) {
                    provider.addPlayer(player);
                }
            }
            for (String player : current) {
                if (!players.stream().anyMatch(p -> p.equalsIgnoreCase(player))) {
                    provider.removePlayer(player);
                }
            }
        }
        logger.info("Whitelist set to " + players.size() + " players");
    }

    public String getMode() {
        return mode;
    }
}
