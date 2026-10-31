package com.gxlydlyf.geyserdetector.paper;

import com.gxlydlyf.geyserdetector.api.BedrockDetector;
import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import com.gxlydlyf.geyserdetector.api.BedrockSource;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The authoritative set of Bedrock players on this server.
 *
 * <p>Written from the plugin-channel thread and read from any thread, so it is
 * backed by a concurrent map and publishes immutable snapshots.
 *
 * <p>Public only so the command and placeholder layers can reach the diagnostic
 * accessors below; the mutators stay package-private.
 */
public final class BedrockRegistry implements BedrockDetector {

    private final Map<UUID, BedrockPlayer> players = new ConcurrentHashMap<>();

    /**
     * Ticks at which a proxy message was last seen. Kept as a timestamp rather
     * than a boolean so a proxy that dies without disconnecting its players
     * eventually stops being reported as available.
     */
    private final AtomicLong lastContactMillis = new AtomicLong();

    private volatile boolean syncInProgress;

    /**
     * Records that a frame from the proxy was just processed.
     *
     * <p>Called only from the channel listener. It must not be triggered by local
     * activity such as a player quitting, or {@link #available()} would start
     * reporting a proxy that is not actually there.
     */
    void markContact() {
        lastContactMillis.set(System.currentTimeMillis());
    }

    void put(BedrockPlayer player) {
        players.put(player.uniqueId(), player);
    }

    Optional<BedrockPlayer> remove(UUID uniqueId) {
        return Optional.ofNullable(players.remove(uniqueId));
    }

    void clear() {
        players.clear();
    }

    void beginSync() {
        syncInProgress = true;
    }

    /**
     * Ends a full-state batch, dropping any tracked player the proxy did not list.
     *
     * <p>This is what makes the replica self-correcting: a player who left while
     * the proxy was down, or who was never announced because the backend was
     * starting, is reconciled here instead of lingering forever.
     *
     * @param seenDuringSync UUIDs the proxy reported in this batch
     */
    void endSync(Collection<UUID> seenDuringSync) {
        syncInProgress = false;
        players.keySet().removeIf(uuid -> !seenDuringSync.contains(uuid));
    }

    boolean isSyncInProgress() {
        return syncInProgress;
    }

    /** Milliseconds since the last message from the proxy, or {@link Long#MAX_VALUE} if never. */
    public long millisSinceContact() {
        long last = lastContactMillis.get();
        return last == 0L ? Long.MAX_VALUE : System.currentTimeMillis() - last;
    }

    // --- BedrockDetector ---

    @Override
    public boolean available() {
        return lastContactMillis.get() != 0L;
    }

    @Override
    public BedrockSource source() {
        return available() ? BedrockSource.PROXY : BedrockSource.NONE;
    }

    @Override
    public boolean isBedrock(UUID uniqueId) {
        return players.containsKey(uniqueId);
    }

    @Override
    public Optional<BedrockPlayer> lookup(UUID uniqueId) {
        return Optional.ofNullable(players.get(uniqueId));
    }

    @Override
    public Collection<BedrockPlayer> online() {
        return List.copyOf(players.values());
    }

    // --- forms ---

    /**
     * How form traffic leaves this server. Set once during startup.
     *
     * <p>Held as a field rather than called directly so the registry has no compile
     * dependency on the plugin-channel classes, which keeps it testable in isolation.
     */
    private volatile FormTransport formTransport;

    interface FormTransport {
        /** @return true when the frame was handed to the server to send */
        boolean sendForm(UUID playerId, String formJson);

        boolean closeForm(UUID playerId);
    }

    void setFormTransport(FormTransport transport) {
        this.formTransport = transport;
    }

    /** The listener receiving form answers. Replaced wholesale on each registration. */
    private volatile java.util.function.BiConsumer<UUID, String> responseListener;

    @Override
    public boolean sendForm(UUID playerId, String formJson) {
        FormTransport transport = this.formTransport;
        if (transport == null || formJson == null) {
            return false;
        }
        // Refuse before crossing the network: a form can only be shown to a player
        // this server already knows is on Bedrock.
        if (!isBedrock(playerId)) {
            return false;
        }
        return transport.sendForm(playerId, formJson);
    }

    @Override
    public boolean closeForm(UUID playerId) {
        FormTransport transport = this.formTransport;
        return transport != null && isBedrock(playerId) && transport.closeForm(playerId);
    }

    @Override
    public void registerResponseListener(java.util.function.BiConsumer<UUID, String> listener) {
        this.responseListener = listener;
    }

    /**
     * Delivers an answer from a Bedrock player to the registered listener.
     *
     * <p>Called from the plugin-channel thread. A listener that throws is contained
     * here so one misbehaving consumer cannot stall the channel.
     */
    void deliverResponse(UUID playerId, String responseJson) {
        java.util.function.BiConsumer<UUID, String> listener = this.responseListener;
        if (listener == null) {
            return;
        }
        listener.accept(playerId, responseJson);
    }
}
