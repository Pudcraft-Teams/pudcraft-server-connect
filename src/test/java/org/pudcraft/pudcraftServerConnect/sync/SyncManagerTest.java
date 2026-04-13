package org.pudcraft.pudcraftServerConnect.sync;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncManagerTest {

    @Test
    void replayedAddSyncIsAckedWhenPlayerIsAlreadyWhitelisted() {
        try (TestFixture fixture = newFixture()) {
            when(fixture.whitelistManager.addPlayer("Alex")).thenReturn(false);
            when(fixture.whitelistManager.isWhitelisted("Alex")).thenReturn(true);

            fixture.syncManager.processSync("sync-add", "Alex", "add");

            verify(fixture.apiClient).postEmpty("/api/sync/sync-add/ack");
        }
    }

    @Test
    void replayedRemoveSyncIsAckedWhenPlayerIsAlreadyAbsent() {
        try (TestFixture fixture = newFixture()) {
            when(fixture.whitelistManager.removePlayer("Alex")).thenReturn(false);
            when(fixture.whitelistManager.isWhitelisted("Alex")).thenReturn(false);

            fixture.syncManager.processSync("sync-remove", "Alex", "remove");

            verify(fixture.apiClient).postEmpty("/api/sync/sync-remove/ack");
        }
    }

    @Test
    void failedAddSyncIsNotAckedWhenPlayerIsStillMissing() {
        try (TestFixture fixture = newFixture()) {
            when(fixture.whitelistManager.addPlayer("Alex")).thenReturn(false);
            when(fixture.whitelistManager.isWhitelisted("Alex")).thenReturn(false);

            fixture.syncManager.processSync("sync-add", "Alex", "add");

            verify(fixture.apiClient, never()).postEmpty(anyString());
        }
    }

    @Test
    void failedRemoveSyncIsNotAckedWhenPlayerIsStillPresent() {
        try (TestFixture fixture = newFixture()) {
            when(fixture.whitelistManager.removePlayer("Alex")).thenReturn(false);
            when(fixture.whitelistManager.isWhitelisted("Alex")).thenReturn(true);

            fixture.syncManager.processSync("sync-remove", "Alex", "remove");

            verify(fixture.apiClient, never()).postEmpty(anyString());
        }
    }

    private TestFixture newFixture() {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class);
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        ApiClient apiClient = mock(ApiClient.class);
        when(apiClient.postEmpty(anyString())).thenReturn(new ApiResponse(200, "{}"));
        WhitelistManager whitelistManager = mock(WhitelistManager.class);
        ConfigManager configManager = mock(ConfigManager.class);
        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTask(eq(plugin), any(Runnable.class));
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        }).when(scheduler).runTaskAsynchronously(eq(plugin), any(Runnable.class));
        SyncManager syncManager = new SyncManager(plugin, apiClient, whitelistManager, configManager);

        return new TestFixture(syncManager, apiClient, whitelistManager, bukkit);
    }

    private static final class TestFixture implements AutoCloseable {
        private final SyncManager syncManager;
        private final ApiClient apiClient;
        private final WhitelistManager whitelistManager;
        private final MockedStatic<Bukkit> bukkit;

        private TestFixture(SyncManager syncManager, ApiClient apiClient,
                            WhitelistManager whitelistManager, MockedStatic<Bukkit> bukkit) {
            this.syncManager = syncManager;
            this.apiClient = apiClient;
            this.whitelistManager = whitelistManager;
            this.bukkit = bukkit;
        }

        @Override
        public void close() {
            bukkit.close();
        }
    }
}
