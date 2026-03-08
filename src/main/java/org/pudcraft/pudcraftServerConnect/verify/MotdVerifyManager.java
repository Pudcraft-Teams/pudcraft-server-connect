package org.pudcraft.pudcraftServerConnect.verify;

import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;

import java.util.Map;
import java.util.logging.Logger;

public class MotdVerifyManager {
    private final PudcraftServerConnect plugin;
    private final ApiClient apiClient;
    private final MessageManager messageManager;
    private final Logger logger;

    public MotdVerifyManager(PudcraftServerConnect plugin, ApiClient apiClient,
                             MessageManager messageManager) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.messageManager = messageManager;
        this.logger = plugin.getLogger();
    }

    /**
     * Claim server ownership via API key.
     * Returns a user-facing message.
     */
    public String claim() {
        ApiResponse response = apiClient.postEmpty("/api/servers/{id}/verify/claim");

        if (response.isSuccess()) {
            logger.info("Server claimed successfully");
            return messageManager.get("verify.success");
        } else if (response.getStatusCode() == 409) {
            return messageManager.get("verify.already-claimed");
        } else {
            return messageManager.get("verify.failed",
                Map.of("reason", response.getError()));
        }
    }
}
