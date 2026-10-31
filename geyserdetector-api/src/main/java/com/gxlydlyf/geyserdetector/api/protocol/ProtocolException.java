package com.gxlydlyf.geyserdetector.api.protocol;

/** Thrown when a plugin-channel frame cannot be understood. */
public class ProtocolException extends Exception {

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
