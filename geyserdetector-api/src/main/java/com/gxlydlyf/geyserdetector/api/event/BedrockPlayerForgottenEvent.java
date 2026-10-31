package com.gxlydlyf.geyserdetector.api.event;

import com.gxlydlyf.geyserdetector.api.BedrockPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired on the downstream server when a Bedrock player is no longer tracked,
 * either because they disconnected or because the proxy told us to forget them.
 *
 * <p>Listeners holding per-player Bedrock state should drop it here.
 */
public class BedrockPlayerForgottenEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final BedrockPlayer bedrockPlayer;

    public BedrockPlayerForgottenEvent(@NotNull Player player, @NotNull BedrockPlayer bedrockPlayer) {
        super(player);
        this.bedrockPlayer = bedrockPlayer;
    }

    /** The details as they were last known. */
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
