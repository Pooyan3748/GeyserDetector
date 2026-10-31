package com.gxlydlyf.geyserdetector.paper.placeholder;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.paper.BedrockRegistry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Exposes Bedrock state to PlaceholderAPI, so scoreboards and TAB can show it
 * without writing any code.
 *
 * <pre>
 *   %geyserdetector_bedrock%    true / false
 *   %geyserdetector_platform%   Android, iOS, Windows, ...
 *   %geyserdetector_version%    Bedrock client version
 *   %geyserdetector_xuid%       Xbox Live id, empty when not linked
 * </pre>
 */
public final class GeyserDetectorExpansion extends PlaceholderExpansion {

    private static final String UNKNOWN = "";

    private final BedrockRegistry registry;

    public GeyserDetectorExpansion(BedrockRegistry registry) {
        this.registry = registry;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "geyserdetector";
    }

    @Override
    public @NotNull String getAuthor() {
        return "gxlydlyf";
    }

    @Override
    public @NotNull String getVersion() {
        return "2.0.0";
    }

    /** False so the expansion follows the plugin's own lifecycle instead of being cached. */
    @Override
    public boolean persist() {
        return false;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return UNKNOWN;
        }
        // Only online players can be Bedrock clients, so skip the registry work otherwise.
        if (!(offlinePlayer instanceof Player player) || !player.isOnline()) {
            return "false".equals(params) ? "false" : UNKNOWN;
        }

        Optional<BedrockPlayer> bedrockPlayer = registry.lookup(player.getUniqueId());
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "bedrock" -> String.valueOf(bedrockPlayer.isPresent());
            case "platform" -> bedrockPlayer.map(BedrockPlayer::platformName).orElse(UNKNOWN);
            case "version" -> bedrockPlayer.map(BedrockPlayer::version).orElse(UNKNOWN);
            case "xuid" -> bedrockPlayer.map(BedrockPlayer::xuid).orElse(UNKNOWN);
            default -> null;
        };
    }
}
