package com.gxlydlyf.geyserdetector.paper;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.api.event.BedrockPlayerDetectedEvent;
import com.gxlydlyf.geyserdetector.api.event.BedrockPlayerForgottenEvent;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockCodec;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockMessage;
import com.gxlydlyf.geyserdetector.api.protocol.ProtocolException;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies frames arriving from the proxy to the registry, then fires the public
 * events.
 *
 * <p>The channel callback runs on a network thread, so nothing here touches world
 * or player state directly. Registry writes are thread-safe and event dispatch is
 * handed to the player's own region thread, which is what keeps this correct on
 * Folia and on Paper alike.
 */
final class ChannelListener implements PluginMessageListener {

    private final Plugin plugin;
    private final BedrockRegistry registry;

    /** UUIDs reported during the batch currently being applied. */
    private final Set<UUID> syncBatch = new HashSet<>();

    ChannelListener(Plugin plugin, BedrockRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] message) {
        BedrockMessage decoded;
        try {
            decoded = BedrockCodec.decode(message);
        } catch (ProtocolException e) {
            plugin.getLogger().warning("Ignoring malformed frame: " + e.getMessage());
            return;
        }

        // Any well-formed frame proves the proxy is alive and talking to us.
        registry.markContact();

        // An if-chain rather than a pattern switch: this build targets Java 17.
        if (decoded instanceof BedrockMessage.SyncBegin) {
            onSyncBegin();
        } else if (decoded instanceof BedrockMessage.Add add) {
            onAdd(add.player());
        } else if (decoded instanceof BedrockMessage.SyncEnd) {
            onSyncEnd();
        } else if (decoded instanceof BedrockMessage.Remove remove) {
            onRemove(remove.uniqueId());
        } else if (decoded instanceof BedrockMessage.FormResponse formResponse) {
            registry.deliverResponse(formResponse.playerId(), formResponse.responseJson());
        } else if (decoded instanceof BedrockMessage.SyncRequest) {
            plugin.getLogger().warning("Received a sync request from the proxy; ignoring.");
        }
        // BedrockMessage.Hello needs no action beyond the contact mark above, and the
        // outgoing-only messages (SendForm, CloseForm) never arrive from the proxy.
    }

    private void onSyncBegin() {
        synchronized (syncBatch) {
            syncBatch.clear();
        }
        registry.beginSync();
    }

    private void onAdd(BedrockPlayer bedrockPlayer) {
        if (registry.isSyncInProgress()) {
            synchronized (syncBatch) {
                syncBatch.add(bedrockPlayer.uniqueId());
            }
        }

        Optional<BedrockPlayer> previous = registry.lookup(bedrockPlayer.uniqueId());
        registry.put(bedrockPlayer);

        // Only announce a change, so a repeated sync does not spam listeners.
        if (previous.filter(existing -> existing.equals(bedrockPlayer)).isEmpty()) {
            dispatch(bedrockPlayer.uniqueId(),
                    player -> new BedrockPlayerDetectedEvent(player, bedrockPlayer));
        }
    }

    private void onSyncEnd() {
        Set<UUID> batch;
        synchronized (syncBatch) {
            batch = Set.copyOf(syncBatch);
            syncBatch.clear();
        }
        registry.endSync(batch);
    }

    private void onRemove(UUID uniqueId) {
        registry.remove(uniqueId).ifPresent(previous ->
                dispatch(uniqueId, player -> new BedrockPlayerForgottenEvent(player, previous)));
    }

    /**
     * Fires an event on the player's region thread.
     *
     * <p>Skipped when the player is already gone: there is no thread to run on and
     * no listener that could act on it.
     */
    private void dispatch(UUID uniqueId, java.util.function.Function<Player, org.bukkit.event.Event> factory) {
        Player player = Bukkit.getPlayer(uniqueId);
        if (player == null) {
            return;
        }
        FoliaScheduler.onPlayer(plugin, player, () -> {
            if (player.isOnline()) {
                Bukkit.getPluginManager().callEvent(factory.apply(player));
            }
        });
    }
}
