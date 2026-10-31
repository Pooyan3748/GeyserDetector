package com.gxlydlyf.geyserdetector.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(
        id = "geyserdetector",
        name = "GeyserDetector-Velocity",
        version = "2.0.0",
        description = "Detects Bedrock players and relays them to the downstream server",
        authors = {"gxlydlyf"}
)
public class GeyserDetectorVelocity {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public GeyserDetectorVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        BedrockTracker tracker = new BedrockTracker();
        BedrockRelay relay = new BedrockRelay(server, logger);

        // A form answer is routed back to the server the player is standing on, which
        // is the same connection the form request arrived through.
        FormBridge formBridge = new FormBridge(server, logger, (playerId, response) ->
                server.getPlayer(playerId).ifPresent(player ->
                        relay.sendFormResponse(player, playerId, response)));

        server.getChannelRegistrar().register(BedrockRelay.CHANNEL);
        server.getEventManager().register(this, new ConnectionListener(tracker, relay, logger));
        server.getEventManager().register(this,
                new BackendRequestListener(tracker, relay, formBridge, logger));

        if (tracker.geyserAvailable()) {
            logger.info("GeyserDetector-Velocity enabled (Geyser detected).");
        } else {
            logger.warn("GeyserDetector-Velocity enabled, but Geyser is not installed on this proxy. "
                    + "Every player will be reported as Java edition.");
        }
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        server.getChannelRegistrar().unregister(BedrockRelay.CHANNEL);
    }
}
