package com.gxlydlyf.geyserdetector.paper;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.api.event.BedrockPlayerForgottenEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

/**
 * Keeps the registry in step with who is actually on the server.
 *
 * <p>The proxy normally sends a removal, but a proxy restart or a dropped
 * connection can lose that message, so the quit event is the backstop.
 */
final class PlayerLifecycleListener implements Listener {

    private final BedrockRegistry registry;

    PlayerLifecycleListener(BedrockRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<BedrockPlayer> forgotten = registry.remove(player.getUniqueId());
        if (forgotten.isEmpty()) {
            return;
        }
        // Called directly rather than through the region scheduler: the player is
        // already leaving, so their region thread may no longer accept work.
        Bukkit.getPluginManager().callEvent(new BedrockPlayerForgottenEvent(player, forgotten.get()));
    }
}
