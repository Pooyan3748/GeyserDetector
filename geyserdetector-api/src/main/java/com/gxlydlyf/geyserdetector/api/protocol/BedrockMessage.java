package com.gxlydlyf.geyserdetector.api.protocol;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;

import java.util.UUID;

/**
 * A single frame on the {@code geyserdetector:main} plugin channel.
 *
 * <p>The proxy and the downstream server share this type, so the two sides cannot
 * drift apart the way hand-written string parsing would allow.
 */
public sealed interface BedrockMessage {

    /** Sent by the proxy once it is ready, so the downstream server can note the peer. */
    record Hello() implements BedrockMessage {
    }

    /**
     * Sent by the downstream server to ask for the complete current picture.
     * The proxy answers with {@link SyncBegin}, zero or more {@link Add}, then {@link SyncEnd}.
     */
    record SyncRequest() implements BedrockMessage {
    }

    /** Opens a full-state batch. Never nested. */
    record SyncBegin() implements BedrockMessage {
    }

    /**
     * Closes a full-state batch. On receiving this the server can forget any of its
     * own online players that were missing from the batch.
     */
    record SyncEnd() implements BedrockMessage {
    }

    /** A player is a Bedrock client, with whatever details the proxy could read. */
    record Add(BedrockPlayer player) implements BedrockMessage {
    }

    /** A player should no longer be tracked as a Bedrock client. */
    record Remove(UUID uniqueId) implements BedrockMessage {
    }

    /**
     * Asks the proxy to show a form to a Bedrock player.
     *
     * <p>The form travels as JSON rather than as a Cumulus object because the two
     * sides do not share a Cumulus version: the proxy uses whatever Cumulus ships
     * inside Geyser, while the backend has its own. JSON is the format both agree on.
     */
    record SendForm(UUID playerId, String formJson) implements BedrockMessage {
    }

    /**
     * The player answered a form, or dismissed it.
     *
     * @param responseJson the raw payload Cumulus received: the submitted values, or
     *                     {@code null}/empty when the player closed the form
     */
    record FormResponse(UUID playerId, String responseJson) implements BedrockMessage {
    }

    /** Asks the proxy to dismiss whatever form the player currently has open. */
    record CloseForm(UUID playerId) implements BedrockMessage {
    }
}
