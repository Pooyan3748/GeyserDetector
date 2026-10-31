package com.gxlydlyf.geyserdetector.velocity;

import com.gxlydlyf.geyserdetector.api.FormSerializer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import org.geysermc.cumulus.form.Form;
import org.geysermc.cumulus.form.impl.FormImpl;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Shows Cumulus forms to Bedrock players and forwards their answers to the backend.
 *
 * <p>The form arrives as JSON from a downstream server and is rebuilt here, so the
 * Cumulus inside Geyser does not have to match the one the backend compiled against.
 *
 * <p>Answers are collected by installing a raw-response consumer on the form itself.
 * Geyser calls that consumer when the client replies, which is the only hook that
 * sees the payload verbatim — the typed result handlers would have already discarded
 * the parts a plugin like an auth UI cares about.
 */
public final class FormBridge {

    private final ProxyServer server;
    private final Logger logger;
    private final BiConsumer<UUID, String> responseSink;

    /**
     * @param responseSink receives the player UUID and the raw response payload, or
     *                     {@code null} when the player dismissed the form
     */
    public FormBridge(ProxyServer server, Logger logger, BiConsumer<UUID, String> responseSink) {
        this.server = server;
        this.logger = logger;
        this.responseSink = responseSink;
    }

    /**
     * Rebuilds the form, attaches the answer hook, and shows it.
     *
     * @return false when the player is not on this proxy, is not a Bedrock client, or
     *         Geyser refused the form
     */
    public boolean sendForm(UUID playerId, String formJson) {
        Optional<Player> maybePlayer = server.getPlayer(playerId);
        if (maybePlayer.isEmpty()) {
            logger.debug("Form requested for {} but they are not on this proxy.", playerId);
            return false;
        }

        Form form;
        try {
            form = FormSerializer.fromJson(formJson);
        } catch (IllegalArgumentException e) {
            logger.warn("Backend sent an unusable form for {}: {}", playerId, e.getMessage());
            return false;
        }

        Optional<GeyserConnection> session = sessionFor(maybePlayer.get());
        if (session.isEmpty()) {
            logger.debug("Form requested for {} but they are not a Bedrock client.", playerId);
            return false;
        }

        attachResponseHook(form, playerId);

        boolean sent = session.get().sendForm(form);
        if (!sent) {
            logger.debug("Geyser refused the form for {}", playerId);
        }
        return sent;
    }

    /** Dismisses whatever form the player has open. */
    public boolean closeForm(UUID playerId) {
        Optional<GeyserConnection> session = server.getPlayer(playerId).flatMap(this::sessionFor);
        if (session.isEmpty()) {
            return false;
        }
        GeyserConnection connection = session.get();
        if (!connection.hasFormOpen()) {
            return false;
        }
        connection.closeForm();
        return true;
    }

    /**
     * Wires the form's raw response consumer to the backend sink.
     *
     * <p>Cumulus hands the consumer the payload the client sent, or {@code null} if
     * the form was dismissed, so the two cases stay distinguishable all the way back
     * to the plugin that opened the form.
     */
    private void attachResponseHook(Form form, UUID playerId) {
        if (!(form instanceof FormImpl<?> impl)) {
            // A form type Cumulus does not implement natively; it cannot report answers.
            logger.warn("Form for {} is not a Cumulus FormImpl, so its response cannot be captured.",
                    playerId);
            return;
        }
        impl.rawResponseConsumer(response -> {
            try {
                responseSink.accept(playerId, response);
            } catch (RuntimeException e) {
                logger.warn("A form response listener threw for {}: {}", playerId, e.toString());
            }
        });
    }

    /**
     * Resolves the Geyser connection for a player.
     *
     * <p>Empty for a Java client, and empty when Geyser is not installed — the
     * {@link IllegalStateException} it throws in that case is expected, not a failure.
     */
    private Optional<GeyserConnection> sessionFor(Player player) {
        try {
            return Optional.ofNullable(GeyserApi.api().connectionByUuid(player.getUniqueId()));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }
}
