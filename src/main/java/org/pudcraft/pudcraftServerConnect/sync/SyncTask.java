package org.pudcraft.pudcraftServerConnect.sync;

import org.bukkit.scheduler.BukkitRunnable;

public class SyncTask extends BukkitRunnable {
    private final SyncManager syncManager;

    public SyncTask(SyncManager syncManager) {
        this.syncManager = syncManager;
    }

    @Override
    public void run() {
        syncManager.pollPending();
    }
}
