package com.gxlydlyf.geyserdetector.api;

/**
 * Where the Bedrock information currently held by this server came from.
 *
 * <p>Exposed so a consumer can distinguish "this player is definitely Java edition"
 * from "detection is not running, so nothing is known at all".
 */
public enum BedrockSource {

    /**
     * A GeyserDetector proxy plugin is feeding this server over the plugin channel.
     * Both {@code true} and {@code false} answers are meaningful.
     */
    PROXY,

    /**
     * No proxy plugin is connected. Every query returns {@code false}, which means
     * "unknown" rather than "confirmed Java edition".
     */
    NONE
}
