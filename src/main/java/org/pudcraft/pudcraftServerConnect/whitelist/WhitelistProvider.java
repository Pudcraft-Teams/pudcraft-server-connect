package org.pudcraft.pudcraftServerConnect.whitelist;

import java.util.List;

public interface WhitelistProvider {
    boolean addPlayer(String username);
    boolean removePlayer(String username);
    boolean isWhitelisted(String username);
    List<String> getWhitelistedPlayers();
}
