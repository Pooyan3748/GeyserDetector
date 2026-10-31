package com.gxlydlyf.geyserdetector.velocity;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockCodec;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockMessage;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.UUID;

/**
 * Pushes Bedrock detection results to the downstream server over the plugin channel.
 *
 * <p>Messages travel on the player's own server connection, so they are only ever
 * sent to the server that player is actually on.
 */
public final class BedrockRelay {

    public static final ChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("geyserdetector", "main");

    private final ProxyServer server;
    private final Logger logger;

    public BedrockRelay(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    /** Tells the downstream server that this player is a Bedrock client. */
    public void sendAdd(Player player, BedrockPlayer bedrockPlayer) {
        send(player, new BedrockMessage.Add(bedrockPlayer));
    }

    /** Tells the downstream server to stop tracking this player. */
    public void sendRemove(Player player, UUID uniqueId) {
        send(player, new BedrockMessage.Remove(uniqueId));
    }

    /** Announces this proxy plugin to the server the player is currently on. */
    public void sendHello(Player player) {
        send(player, new BedrockMessage.Hello());
    }

    /**
     * Returns a form answer to the backend server the player is on.
     *
     * @param responseJson the submitted values, or {@code null} when the player
     *                     dismissed the form
     */
    public void sendFormResponse(Player player, UUID playerId, String responseJson) {
        send(player, new BedrockMessage.FormResponse(playerId, responseJson));
    }

    /**
     * Replies to a sync request with the complete current picture.
     *
     * <p>Bracketed by begin/end so the downstream server can drop any of its own
     * players that are missing from the batch — that is what makes this recover
     * from a downstream restart, a proxy reload, or a player who joined while the
     * backend was still starting.
     */
    public void sendFullSync(Player requester, Collection<BedrockPlayer> online) {
        send(requester, new BedrockMessage.SyncBegin());
        for (BedrockPlayer bedrockPlayer : online) {
            send(requester, new BedrockMessage.Add(bedrockPlayer));
        }
        send(requester, new BedrockMessage.SyncEnd());
    }

    /**
     * Sends a sync request to every server that has a player connected, so the
     * backend learns about a freshly started proxy without waiting for a rejoin.
     */
    public void broadcastSyncRequest() {
        for (RegisteredServer registeredServer : server.getAllServers()) {
            registeredServer.getPlayersConnected().stream().findFirst().ifPresent(player ->
                    send(player, new BedrockMessage.SyncRequest()));
        }
    }

    private void send(Player player, BedrockMessage message) {
        byte[] payload = BedrockCodec.encode(message);
        player.getCurrentServer().ifPresentOrElse(
                connection -> {
                    if (!connection.sendPluginMessage(CHANNEL, payload)) {
                        logger.debug("Downstream server did not accept {} for {}",
                                message.getClass().getSimpleName(), player.getUsername());
                    }
                },
                () -> logger.debug("No server connection for {}, dropping {}",
                        player.getUsername(), message.getClass().getSimpleName()));
    }
}
