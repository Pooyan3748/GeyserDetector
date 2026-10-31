package com.gxlydlyf.geyserdetector.velocity;

import com.gxlydlyf.geyserdetector.api.protocol.BedrockCodec;
import com.gxlydlyf.geyserdetector.api.protocol.BedrockMessage;
import com.gxlydlyf.geyserdetector.api.protocol.ProtocolException;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.messages.ChannelMessageSink;
import org.slf4j.Logger;

/**
 * Answers sync requests arriving from downstream servers.
 *
 * <p>A downstream server asks for a full picture on its own startup. Without this,
 * players already connected when the backend restarts would stay invisible to it
 * until they rejoined or switched servers.
 */
public final class BackendRequestListener {

    private final BedrockTracker tracker;
    private final BedrockRelay relay;
    private final FormBridge formBridge;
    private final Logger logger;

    public BackendRequestListener(BedrockTracker tracker, BedrockRelay relay,
                                  FormBridge formBridge, Logger logger) {
        this.tracker = tracker;
        this.relay = relay;
        this.formBridge = formBridge;
        this.logger = logger;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!BedrockRelay.CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        // Requests come from a backend server, so the player is the message target.
        ChannelMessageSink sink = event.getTarget();
        if (!(sink instanceof Player requester)) {
            return;
        }

        BedrockMessage message;
        try {
            message = BedrockCodec.decode(event.getData());
        } catch (ProtocolException e) {
            logger.warn("Ignoring malformed frame from {}: {}", requester.getUsername(), e.getMessage());
            return;
        }

        if (message instanceof BedrockMessage.SyncRequest) {
            logger.debug("Full sync requested by the server behind {}", requester.getUsername());
            relay.sendFullSync(requester, tracker.tracked());
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        } else if (message instanceof BedrockMessage.SendForm sendForm) {
            boolean shown = formBridge.sendForm(sendForm.playerId(), sendForm.formJson());
            if (!shown) {
                logger.debug("Could not show the form requested for {}", sendForm.playerId());
            }
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        } else if (message instanceof BedrockMessage.CloseForm closeForm) {
            formBridge.closeForm(closeForm.playerId());
            event.setResult(PluginMessageEvent.ForwardResult.handled());
        }
    }
}
