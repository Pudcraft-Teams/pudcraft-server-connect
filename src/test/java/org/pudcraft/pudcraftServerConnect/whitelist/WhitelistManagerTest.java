package org.pudcraft.pudcraftServerConnect.whitelist;

import org.bukkit.Server;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.config.PluginConfig;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhitelistManagerTest {
    @Mock
    private PudcraftServerConnect plugin;
    @Mock
    private Server server;
    @Mock
    private PluginManager pluginManager;
    @Mock
    private ConfigManager configManager;
    @Mock
    private PluginConfig pluginConfig;
    @Mock
    private MessageManager messageManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        lenient().when(plugin.getServer()).thenReturn(server);
        lenient().when(server.getPluginManager()).thenReturn(pluginManager);
        lenient().when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(configManager.getPluginConfig()).thenReturn(pluginConfig);
        lenient().when(configManager.getMessageManager()).thenReturn(messageManager);
    }

    @Test
    void shutdownUnregistersPluginManagedWhitelistListener() throws Exception {
        when(pluginConfig.getWhitelistMode()).thenReturn("plugin");

        WhitelistManager manager = new WhitelistManager(plugin, configManager);
        PluginWhitelistProvider provider = assertInstanceOf(
            PluginWhitelistProvider.class,
            readField(manager, "provider")
        );

        try (MockedStatic<HandlerList> handlerList = mockStatic(HandlerList.class)) {
            invokeShutdown(manager);
            handlerList.verify(() -> HandlerList.unregisterAll(provider));
        }
    }

    @Test
    void shutdownDoesNothingInNativeMode() throws Exception {
        when(pluginConfig.getWhitelistMode()).thenReturn("native");

        WhitelistManager manager = new WhitelistManager(plugin, configManager);

        try (MockedStatic<HandlerList> handlerList = mockStatic(HandlerList.class)) {
            invokeShutdown(manager);
            handlerList.verifyNoInteractions();
        }
    }

    private static void invokeShutdown(WhitelistManager manager) throws Exception {
        Method shutdown = WhitelistManager.class.getDeclaredMethod("shutdown");
        shutdown.setAccessible(true);
        shutdown.invoke(manager);
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }
}
