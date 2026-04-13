package org.pudcraft.pudcraftServerConnect.command;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.pudcraft.pudcraftServerConnect.PudcraftServerConnect;
import org.pudcraft.pudcraftServerConnect.config.ConfigManager;
import org.pudcraft.pudcraftServerConnect.config.MessageManager;
import org.pudcraft.pudcraftServerConnect.update.UpdateChecker;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MainCommandTest {

    @Test
    void reloadCommandSendsFailureMessageWhenReloadFutureFails() {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class);
        ConfigManager configManager = mock(ConfigManager.class);
        MessageManager messageManager = mock(MessageManager.class);
        UpdateChecker updateChecker = mock(UpdateChecker.class);
        CommandSender sender = mock(CommandSender.class);
        Command command = mock(Command.class);
        Server server = mock(Server.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);

        when(configManager.getMessageManager()).thenReturn(messageManager);
        when(sender.hasPermission("pudcraft.reload")).thenReturn(true);
        when(plugin.reload()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return null;
        });
        when(messageManager.get("config.reload-success")).thenReturn("reload ok");
        when(messageManager.get(eq("config.reload-failed"), eq(Map.of("reason", "boom")))).thenReturn("reload failed: boom");

        MainCommand mainCommand = new MainCommand(plugin, configManager, null, null, null, updateChecker);
        mainCommand.onCommand(sender, command, "pudcraft", new String[]{"reload"});

        verify(sender).sendMessage("reload failed: boom");
        verify(sender, never()).sendMessage("reload ok");
    }
}
