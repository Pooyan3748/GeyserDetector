package com.gxlydlyf.geyserdetector.paper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Runs work on the right thread under both Paper and Folia.
 *
 * <p>Folia has no main thread, so {@code Bukkit.getScheduler()} is not usable and
 * any code that touches an entity must run on that entity's own region thread.
 * Everything here goes through the region-aware schedulers that Paper also
 * provides, so a single code path works on both platforms.
 */
final class FoliaScheduler {

    private FoliaScheduler() {
    }

    /**
     * Runs {@code task} on the thread that owns {@code player}.
     *
     * <p>Falls back to the global region scheduler when the player is offline by
     * the time the task would run, because their region thread no longer exists.
     */
    static void onPlayer(Plugin plugin, Player player, Runnable task) {
        if (!player.isOnline()) {
            global(plugin, task);
            return;
        }
        player.getScheduler().execute(plugin, task, () -> global(plugin, task), 1L);
    }

    /** Runs {@code task} on the global region thread. */
    static void global(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    /**
     * Runs {@code task} asynchronously after a delay.
     *
     * @param delayTicks delay in ticks, converted to milliseconds
     */
    static void asyncLater(Plugin plugin, Runnable task, long delayTicks) {
        Bukkit.getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(),
                Math.max(1L, delayTicks) * 50L, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}
