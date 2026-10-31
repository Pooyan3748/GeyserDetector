package com.gxlydlyf.geyserdetector.velocity;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerClientBrandEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Drives detection from the proxy's player lifecycle.
 *
 * <p>Detection runs on {@link ServerPostConnectEvent} rather than on login because
 * the plugin channel needs a server connection to send on. The post-connect event
 * is also the one that fires again after a server switch, so a player moving
 * between backends re-announces themselves for free.
 */
public final class ConnectionListener {

    private final BedrockTracker tracker;
    private final BedrockRelay relay;
    private final Logger logger;

    public ConnectionListener(BedrockTracker tracker, BedrockRelay relay, Logger logger) {
        this.tracker = tracker;
        this.relay = relay;
        this.logger = logger;
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        Optional<BedrockPlayer> detected = tracker.resolve(player);
        if (detected.isEmpty()) {
            // The player is a Java client. If we previously believed otherwise, correct it.
            tracker.forget(player.getUniqueId())
                    .ifPresent(previous -> relay.sendRemove(player, previous.uniqueId()));
            return;
        }

        BedrockPlayer bedrockPlayer = detected.get();
        logger.debug("{} is a Bedrock client on {} ({})",
                player.getUsername(), bedrockPlayer.platformName(), bedrockPlayer.version());
        relay.sendHello(player);
        relay.sendAdd(player, bedrockPlayer);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        tracker.forget(event.getPlayer().getUniqueId());
    }

    /**
     * Records the client brand purely so a misconfigured setup is visible in logs.
     *
     * <p>The brand is not used for detection: it is trivially spoofable by a client
     * (a Java client can claim to be "Geyser"), so trusting it would let anyone
     * opt into Bedrock-only behaviour. Geyser's own API is the authoritative check.
     */
    @Subscribe
    public void onClientBrand(PlayerClientBrandEvent event) {
        if (!logger.isDebugEnabled()) {
            return;
        }
        String brand = event.getBrand();
        boolean claimsGeyser = brand != null && brand.toLowerCase(java.util.Locale.ROOT).contains("geyser");
        boolean actuallyBedrock = tracker.cached(event.getPlayer().getUniqueId()).isPresent();
        if (claimsGeyser != actuallyBedrock) {
            logger.warn("Client brand/detection mismatch for {}: brand='{}' but GeyserApi says bedrock={}",
                    event.getPlayer().getUsername(), brand, actuallyBedrock);
        }
    }

    /** Used by the sync responder to include players before their post-connect ran. */
    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        tracker.resolve(event.getPlayer());
    }
}
