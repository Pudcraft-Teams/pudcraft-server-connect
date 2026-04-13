package org.pudcraft.pudcraftServerConnect.status;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.PluginConfig;
import org.pudcraft.pudcraftServerConnect.network.ApiClient;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StatusReporterTest {

    @Test
    void startSchedulesRepeatingTaskOnMainThread() {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class);
        ApiClient apiClient = mock(ApiClient.class);
        ConfigManager configManager = mock(ConfigManager.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);

        when(configManager.getPluginConfig()).thenReturn(pluginConfig);
        when(pluginConfig.getReportIntervalSeconds()).thenReturn(60);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong())).thenReturn(task);

        StatusReporter reporter = new StatusReporter(plugin, apiClient, configManager);
        reporter.start();

        verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(100L), eq(1200L));
    }

    @Test
    void reportOfflineSendsAsyncWithoutBlockingPost() {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class);
        ApiClient apiClient = mock(ApiClient.class);
        ConfigManager configManager = mock(ConfigManager.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        Server server = mock(Server.class);

        when(configManager.getPluginConfig()).thenReturn(pluginConfig);
        when(pluginConfig.isReportTps()).thenReturn(false);
        when(pluginConfig.isReportMemory()).thenReturn(false);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getServer()).thenReturn(server);
        Collection<? extends Player> onlinePlayers = List.<Player>of();
        doReturn(onlinePlayers).when(server).getOnlinePlayers();
        when(server.getMaxPlayers()).thenReturn(20);
        when(server.getVersion()).thenReturn("Paper test");
        when(apiClient.postAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(new ApiResponse(200, "{}")));

        StatusReporter reporter = new StatusReporter(plugin, apiClient, configManager);
        reporter.reportOffline();

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).postAsync(eq("/api/servers/{id}/status/report"), bodyCaptor.capture());
        verify(apiClient, never()).post(any(), any());
        assertTrue(bodyCaptor.getValue().contains("\"online\":false"));
    }
}
