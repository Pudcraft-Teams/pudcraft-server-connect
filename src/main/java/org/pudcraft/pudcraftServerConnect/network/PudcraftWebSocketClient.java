package org.pudcraft.pudcraftServerConnect.network;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class PudcraftWebSocketClient extends WebSocketClient {
    private final Logger logger;
    private final Consumer<String> messageHandler;
    private final int initialDelay;
    private final int maxDelay;
    private final ScheduledExecutorService reconnectExecutor;
    private int currentDelay;
    private volatile boolean intentionallyClosed = false;

    public PudcraftWebSocketClient(URI serverUri, Logger logger,
                                    Consumer<String> messageHandler,
                                    int initialDelaySeconds,
                                    int maxDelaySeconds) {
        super(serverUri);
        this.logger = logger;
        this.messageHandler = messageHandler;
        this.initialDelay = initialDelaySeconds;
        this.maxDelay = maxDelaySeconds;
        this.currentDelay = initialDelaySeconds;
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PudCraft-WS-Reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        logger.info("WebSocket connected");
        currentDelay = initialDelay; // Reset backoff on successful connection
    }

    @Override
    public void onMessage(String message) {
        messageHandler.accept(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        logger.info("WebSocket closed (code=" + code + ", reason=" + reason + ", remote=" + remote + ")");
        if (!intentionallyClosed) {
            scheduleReconnect();
        }
    }

    @Override
    public void onError(Exception ex) {
        logger.warning("WebSocket error: " + ex.getMessage());
    }

    private void scheduleReconnect() {
        logger.info("Scheduling WebSocket reconnect in " + currentDelay + " seconds...");
        reconnectExecutor.schedule(() -> {
            if (!intentionallyClosed) {
                try {
                    reconnect();
                } catch (Exception e) {
                    logger.warning("WebSocket reconnect failed: " + e.getMessage());
                    scheduleReconnect();
                }
            }
        }, currentDelay, TimeUnit.SECONDS);
        // Exponential backoff
        currentDelay = Math.min(currentDelay * 2, maxDelay);
    }

    public void shutdown() {
        intentionallyClosed = true;
        reconnectExecutor.shutdownNow();
        try {
            closeBlocking();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean isConnectedAndOpen() {
        return isOpen();
    }
}
