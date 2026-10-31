package com.gxlydlyf.geyserdetector.api;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of which players on this server are Bedrock clients.
 *
 * <p>Registered with Bukkit's {@code ServicesManager}, so a plugin can obtain it
 * without a compile-time dependency on this artifact:
 *
 * <pre>{@code
 * RegisteredServiceProvider<BedrockDetector> reg =
 *         Bukkit.getServicesManager().getRegistration(BedrockDetector.class);
 * BedrockDetector detector = reg == null ? null : reg.getProvider();
 * }</pre>
 *
 * <p>Implementations must be safe to call from any thread.
 */
public interface BedrockDetector {

    /** True when a proxy plugin is currently supplying detection data. */
    boolean available();

    /** Where the data is coming from. */
    BedrockSource source();

    /** True when the given player is a Bedrock client. */
    boolean isBedrock(UUID uniqueId);

    /** The Bedrock details for a player, or empty when they are not a Bedrock client. */
    Optional<BedrockPlayer> lookup(UUID uniqueId);

    /** Every Bedrock player currently known to be on this server. */
    Collection<BedrockPlayer> online();

    /**
     * Asks the proxy to show a Cumulus form to a Bedrock player.
     *
     * <p>The form is serialized here and rebuilt on the proxy, because the two ends
     * do not share a Cumulus version. The player's answer arrives at whoever
     * registered a response listener.
     *
     * @param playerId the Bedrock player's UUID
     * @param formJson a Cumulus form serialized to JSON
     * @return {@code false} when the player is not Bedrock, is not tracked, or the
     *         request could not be sent — never throws for those cases
     */
    boolean sendForm(UUID playerId, String formJson);

    /**
     * Asks the proxy to dismiss whatever form a Bedrock player has open.
     *
     * @return {@code false} when the player is not Bedrock, is not tracked, or the
     *         request could not be sent
     */
    boolean closeForm(UUID playerId);

    /**
     * Registers a callback receiving every form response from Bedrock players.
     *
     * <p>The UUID identifies the player; the string is the raw payload Cumulus
     * produced — the submitted values as JSON, or {@code null} when the player
     * dismissed the form. Registering replaces any previous listener.
     */
    void registerResponseListener(java.util.function.BiConsumer<UUID, String> listener);
}
