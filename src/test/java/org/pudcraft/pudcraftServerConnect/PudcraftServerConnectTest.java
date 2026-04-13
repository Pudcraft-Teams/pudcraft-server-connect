package org.pudcraft.pudcraftServerConnect;

import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.config.PluginConfig;
import org.pudcraft.pudcraftServerConnect.network.ApiResponse;
import org.pudcraft.pudcraftServerConnect.status.StatusReporter;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.update.UpdateChecker;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class PudcraftServerConnectTest {
    @Test
    void shutdownServicesShutsDownWhitelistManager() throws Exception {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class, withSettings().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
        WhitelistManager whitelistManager = mock(WhitelistManager.class);

        setField(plugin, "updateChecker", mock(UpdateChecker.class));
        StatusReporter statusReporter = mock(StatusReporter.class);
        when(statusReporter.reportOffline()).thenReturn(CompletableFuture.completedFuture(new ApiResponse(200, "{}")));
        setField(plugin, "statusReporter", statusReporter);
        setField(plugin, "syncManager", mock(SyncManager.class));
        setField(plugin, "whitelistManager", whitelistManager);

        invokeShutdownServices(plugin);

        boolean shutdownCalled = mockingDetails(whitelistManager).getInvocations().stream()
            .anyMatch(invocation -> invocation.getMethod().getName().equals("shutdown"));

        org.junit.jupiter.api.Assertions.assertTrue(shutdownCalled, "whitelist manager shutdown should be invoked");
    }

    @Test
    void reloadWaitsForOfflineReportBeforeRestartingServices() throws Exception {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class, withSettings().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
        ConfigManager configManager = mock(ConfigManager.class);
        PluginConfig pluginConfig = mock(PluginConfig.class);
        MessageManager messageManager = mock(MessageManager.class);
        StatusReporter statusReporter = mock(StatusReporter.class);
        UpdateChecker updateChecker = mock(UpdateChecker.class);
        SyncManager syncManager = mock(SyncManager.class);
        WhitelistManager whitelistManager = mock(WhitelistManager.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        CompletableFuture<ApiResponse> offlineFuture = new CompletableFuture<>();

        doReturn(Logger.getLogger("test")).when(plugin).getLogger();
        doReturn(server).when(plugin).getServer();
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        });
        doReturn((PluginCommand) null).when(plugin).getCommand("pudcraft");
        when(configManager.getPluginConfig()).thenReturn(pluginConfig);
        when(configManager.getMessageManager()).thenReturn(messageManager);
        when(pluginConfig.isUpdateEnabled()).thenReturn(false);
        when(pluginConfig.isConfigured()).thenReturn(false);
        when(messageManager.getRaw(any())).thenReturn("msg");
        when(statusReporter.reportOffline()).thenReturn(offlineFuture);

        setField(plugin, "configManager", configManager);
        setField(plugin, "updateChecker", updateChecker);
        setField(plugin, "statusReporter", statusReporter);
        setField(plugin, "syncManager", syncManager);
        setField(plugin, "whitelistManager", whitelistManager);

        CompletableFuture<Void> reloadFuture = plugin.reload();

        verify(configManager, never()).reload();
        assertFalse(reloadFuture.isDone(), "reload should wait for offline report completion");

        offlineFuture.complete(new ApiResponse(200, "{}"));

        verify(configManager).reload();
        assertTrue(reloadFuture.isDone(), "reload should complete after offline report finishes");
    }

    private static void invokeShutdownServices(PudcraftServerConnect plugin) throws Exception {
        Method method = PudcraftServerConnect.class.getDeclaredMethod("shutdownServices");
        method.setAccessible(true);
        method.invoke(plugin);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
