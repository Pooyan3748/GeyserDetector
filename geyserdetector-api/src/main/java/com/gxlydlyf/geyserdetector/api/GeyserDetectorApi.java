package com.gxlydlyf.geyserdetector.api;

import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.Form;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Static entry point for consumers that would rather not touch Bukkit's service
 * registry. Both this class and {@link BedrockDetector} read the same underlying
 * registry, so mixing them is safe.
 *
 * <pre>{@code
 * if (GeyserDetectorApi.isBedrock(player)) { ... }
 * }</pre>
 *
 * <p>Every method degrades to a safe answer when the plugin is absent: {@code false}
 * and {@link Optional#empty()} rather than a thrown exception. Check
 * {@link #available()} when "not detected" and "not detecting" need to be told apart.
 */
public final class GeyserDetectorApi {

    private static volatile Supplier<BedrockDetector> resolver;
    private static volatile BedrockDetector fallback;

    private GeyserDetectorApi() {
    }

    /**
     * Installs the resolver used by every static accessor. Called by the plugin
     * during startup; consumers never call this.
     */
    public static void install(@NotNull Supplier<BedrockDetector> detectorSupplier) {
        resolver = detectorSupplier;
        // Seed the cache so a caller racing startup still gets a working instance.
        fallback = detectorSupplier.get();
    }

    /** Removes the resolver, so the accessors fall back to a null detector. */
    public static void uninstall() {
        resolver = null;
        fallback = null;
    }

    private static @Nullable BedrockDetector detector() {
        Supplier<BedrockDetector> current = resolver;
        if (current != null) {
            try {
                BedrockDetector resolved = current.get();
                if (resolved != null) {
                    return resolved;
                }
            } catch (IllegalStateException e) {
                // Bukkit is shutting down; fall through to the cached instance.
            }
        }
        return fallback;
    }

    /** True when a proxy plugin is currently supplying detection data. */
    public static boolean available() {
        BedrockDetector current = detector();
        return current != null && current.available();
    }

    /** Where the data is coming from, or {@link BedrockSource#NONE} when nothing is supplying it. */
    @NotNull
    public static BedrockSource source() {
        BedrockDetector current = detector();
        return current == null ? BedrockSource.NONE : current.source();
    }

    /** True when the given player is a Bedrock client. */
    public static boolean isBedrock(@NotNull UUID uniqueId) {
        BedrockDetector current = detector();
        return current != null && current.isBedrock(uniqueId);
    }

    /** True when the given player is a Bedrock client. */
    public static boolean isBedrock(@NotNull Player player) {
        return isBedrock(player.getUniqueId());
    }

    /** The Bedrock details for a player, or empty when they are not a Bedrock client. */
    @NotNull
    public static Optional<BedrockPlayer> lookup(@NotNull UUID uniqueId) {
        BedrockDetector current = detector();
        return current == null ? Optional.empty() : current.lookup(uniqueId);
    }

    /** The Bedrock details for a player, or empty when they are not a Bedrock client. */
    @NotNull
    public static Optional<BedrockPlayer> lookup(@NotNull Player player) {
        return lookup(player.getUniqueId());
    }

    /** Every Bedrock player currently known to be on this server. */
    @NotNull
    public static Collection<BedrockPlayer> online() {
        BedrockDetector current = detector();
        return current == null ? java.util.List.of() : current.online();
    }

    // --- forms ---

    /**
     * Shows a Cumulus form to a Bedrock player.
     *
     * <p>The form is serialized here, carried over the plugin channel, and rebuilt
     * on the proxy, so the Cumulus version on this server does not have to match
     * the one inside Geyser.
     *
     * <pre>{@code
     * CustomForm form = CustomForm.builder()
     *         .title("Login")
     *         .input("Password", "")
     *         .build();
     * GeyserDetectorApi.sendForm(player, form);
     * }</pre>
     *
     * <p>Returns {@code false} — rather than throwing — when the player is not a
     * Bedrock client, is not tracked, or no proxy is connected. Answers arrive at
     * the listener registered with {@link #registerResponseListener}.
     */
    public static boolean sendForm(@NotNull Player player, @NotNull Form form) {
        BedrockDetector current = detector();
        if (current == null) {
            return false;
        }
        String json;
        try {
            json = FormSerializer.toJson(form);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return current.sendForm(player.getUniqueId(), json);
    }

    /** Asks the proxy to dismiss whatever form the player has open. */
    public static boolean closeForm(@NotNull Player player) {
        BedrockDetector current = detector();
        return current != null && current.closeForm(player.getUniqueId());
    }

    /**
     * Registers a callback receiving every form response from Bedrock players.
     *
     * <pre>{@code
     * GeyserDetectorApi.registerResponseListener((uuid, json) -> {
     *     if (json == null) {
     *         // the player dismissed the form
     *     }
     *     // otherwise json holds the submitted values
     * });
     * }</pre>
     *
     * <p>Registering replaces any previous listener. Pass {@code null} to clear.
     */
    public static void registerResponseListener(@Nullable BiConsumer<UUID, String> listener) {
        BedrockDetector current = detector();
        if (current != null) {
            current.registerResponseListener(listener);
        }
    }
}
