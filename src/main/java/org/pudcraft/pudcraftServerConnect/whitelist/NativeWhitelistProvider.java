package org.pudcraft.pudcraftServerConnect.whitelist;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class NativeWhitelistProvider implements WhitelistProvider {
    private final Logger logger;

    public NativeWhitelistProvider(Logger logger) {
        this.logger = logger;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean addPlayer(String username) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            player.setWhitelisted(true);
            logger.info("Added " + username + " to native whitelist");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to add " + username + " to native whitelist: " + e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean removePlayer(String username) {
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            player.setWhitelisted(false);
            logger.info("Removed " + username + " from native whitelist");
            return true;
        } catch (Exception e) {
            logger.warning("Failed to remove " + username + " from native whitelist: " + e.getMessage());
            return false;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isWhitelisted(String username) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(username);
        return player.isWhitelisted();
    }

    @Override
    public List<String> getWhitelistedPlayers() {
        return Bukkit.getWhitelistedPlayers().stream()
            .map(OfflinePlayer::getName)
            .filter(name -> name != null)
            .collect(Collectors.toList());
    }
}
