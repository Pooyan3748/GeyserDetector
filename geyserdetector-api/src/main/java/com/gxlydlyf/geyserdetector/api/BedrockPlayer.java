package com.gxlydlyf.geyserdetector.api;

import java.util.UUID;

/**
 * An immutable snapshot of a Bedrock player as seen by the proxy.
 *
 * @param uniqueId   the Java-edition UUID, identical on the proxy and the downstream
 *                   server because the network uses modern player-info forwarding
 * @param username   the Java username the player connected with
 * @param platform   the device family the client reported
 * @param version    the Bedrock client version string, e.g. {@code "1.21.44"}
 * @param xuid       the Xbox Live user id; may be empty when unavailable
 */
public record BedrockPlayer(
        UUID uniqueId,
        String username,
        BedrockPlatform platform,
        String version,
        String xuid
) {
    public BedrockPlayer {
        if (uniqueId == null) {
            throw new IllegalArgumentException("uniqueId cannot be null");
        }
        platform = platform == null ? BedrockPlatform.UNKNOWN : platform;
        username = username == null ? "" : username;
        version = version == null ? "" : version;
        xuid = xuid == null ? "" : xuid;
    }

    /** Convenience for {@link #platform()}{@code .displayName()}. */
    public String platformName() {
        return platform.displayName();
    }

    /** True when the client reported an Xbox Live user id. */
    public boolean hasXuid() {
        return !xuid.isEmpty();
    }
}
