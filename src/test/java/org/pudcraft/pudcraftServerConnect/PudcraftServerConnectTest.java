package org.pudcraft.pudcraftServerConnect;

import org.junit.jupiter.api.Test;
import org.pudcraft.pudcraftServerConnect.status.StatusReporter;
import org.pudcraft.pudcraftServerConnect.sync.SyncManager;
import org.pudcraft.pudcraftServerConnect.update.UpdateChecker;
import org.pudcraft.pudcraftServerConnect.whitelist.WhitelistManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.withSettings;

class PudcraftServerConnectTest {
    @Test
    void shutdownServicesShutsDownWhitelistManager() throws Exception {
        PudcraftServerConnect plugin = mock(PudcraftServerConnect.class, withSettings().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
        WhitelistManager whitelistManager = mock(WhitelistManager.class);

        setField(plugin, "updateChecker", mock(UpdateChecker.class));
        setField(plugin, "statusReporter", mock(StatusReporter.class));
        setField(plugin, "syncManager", mock(SyncManager.class));
        setField(plugin, "whitelistManager", whitelistManager);

        invokeShutdownServices(plugin);

        boolean shutdownCalled = mockingDetails(whitelistManager).getInvocations().stream()
            .anyMatch(invocation -> invocation.getMethod().getName().equals("shutdown"));

        org.junit.jupiter.api.Assertions.assertTrue(shutdownCalled, "whitelist manager shutdown should be invoked");
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
