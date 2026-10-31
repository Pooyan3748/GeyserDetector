package com.gxlydlyf.geyserdetector.api;

/**
 * The device family a Bedrock player is connecting from.
 *
 * <p>Deliberately independent of Geyser's own enum so that consumers of this API
 * never need Geyser on their classpath, and so the wire format stays stable if
 * Geyser renames or adds platforms.
 */
public enum BedrockPlatform {
    UNKNOWN("Unknown"),
    GOOGLE("Android"),
    IOS("iOS"),
    OSX("macOS"),
    AMAZON("Amazon"),
    GEARVR("Gear VR"),
    HOLOLENS("HoloLens"),
    UWP("Windows (UWP)"),
    WIN32("Windows"),
    DEDICATED("Dedicated"),
    TVOS("tvOS"),
    PS4("PlayStation 4"),
    NX("Nintendo Switch"),
    XBOX("Xbox"),
    WINDOWS_PHONE("Windows Phone"),
    LINUX("Linux");

    private final String displayName;

    BedrockPlatform(String displayName) {
        this.displayName = displayName;
    }

    /** A human-readable name suitable for messages and placeholders. */
    public String displayName() {
        return displayName;
    }

    /**
     * Maps a Geyser platform name (or this enum's own name) onto a constant.
     * Returns {@link #UNKNOWN} for anything unrecognised rather than throwing,
     * so an upstream Geyser update can never break detection.
     */
    public static BedrockPlatform fromName(String name) {
        if (name == null) {
            return UNKNOWN;
        }
        String normalised = name.trim().toUpperCase(java.util.Locale.ROOT);
        for (BedrockPlatform platform : values()) {
            if (platform.name().equals(normalised)) {
                return platform;
            }
        }
        return UNKNOWN;
    }
}
