package com.gxlydlyf.geyserdetector.api.event;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the downstream server once a Bedrock player has been identified.
 *
 * <p>Not cancellable: detection reports a fact about the client, it does not
 * gate anything. A listener that wants to react differently should branch on
 * {@link #bedrockPlayer()} rather than trying to veto the event.
 */
public class BedrockPlayerDetectedEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BedrockPlayer bedrockPlayer;

    public BedrockPlayerDetectedEvent(@NotNull Player player, @NotNull BedrockPlayer bedrockPlayer) {
        super(player);
        this.bedrockPlayer = bedrockPlayer;
    }

    /** The full Bedrock details, including platform, version and XUID. */
    @NotNull
    public BedrockPlayer bedrockPlayer() {
        return bedrockPlayer;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
