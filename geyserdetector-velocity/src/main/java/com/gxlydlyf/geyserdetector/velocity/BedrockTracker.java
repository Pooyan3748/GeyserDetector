package com.gxlydlyf.geyserdetector.velocity;

import com.gxlydlyf.geyserdetector.api.BedrockPlatform;
import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.velocitypowered.api.proxy.Player;
import org.geysermc.api.connection.Connection;
import org.geysermc.geyser.api.GeyserApi;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Asks Geyser which connected players are Bedrock clients, and remembers the answer.
 *
 * <p>Caching matters because Geyser's lookup runs on every join and on every
 * disconnect, and because the platform/version details are read from the Geyser
 * connection object rather than re-parsed from anything.
 */
public final class BedrockTracker {

    private final Map<UUID, BedrockPlayer> tracked = new ConcurrentHashMap<>();

    /** True when the Geyser API can be reached at all. */
    public boolean geyserAvailable() {
        try {
            GeyserApi.api();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /**
     * Resolves the Bedrock details for a player, or empty when they are a Java client.
     *
     * <p>When Geyser is absent every answer is empty, which is why callers report
     * {@link com.gxlydlyf.geyserdetector.api.BedrockSource#NONE} rather than claiming
     * the player is confirmed Java edition.
     */
    public Optional<BedrockPlayer> resolve(Player player) {
        Connection connection;
        try {
            connection = GeyserApi.api().connectionByUuid(player.getUniqueId());
        } catch (IllegalStateException e) {
            // Geyser is not installed on this proxy.
            return Optional.empty();
        }
        if (connection == null) {
            return Optional.empty();
        }

        // Geyser overrides BedrockPlatform.toString() to return a display name such as
        // "Android", so the enum constant is the only stable thing to match on here.
        BedrockPlatform platform = BedrockPlatform.fromName(connection.platform().name());
        BedrockPlayer bedrockPlayer = new BedrockPlayer(
                player.getUniqueId(),
                player.getUsername(),
                platform,
                safe(connection.version()),
                safe(connection.xuid()));
        tracked.put(player.getUniqueId(), bedrockPlayer);
        return Optional.of(bedrockPlayer);
    }

    /** The cached entry for a player, without re-querying Geyser. */
    public Optional<BedrockPlayer> cached(UUID uniqueId) {
        return Optional.ofNullable(tracked.get(uniqueId));
    }

    /** Drops a player from the cache, returning what was removed. */
    public Optional<BedrockPlayer> forget(UUID uniqueId) {
        return Optional.ofNullable(tracked.remove(uniqueId));
    }

    /** Every Bedrock player currently cached. */
    public Collection<BedrockPlayer> tracked() {
        return List.copyOf(tracked.values());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
