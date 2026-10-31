package com.gxlydlyf.geyserdetector.api.protocol;

import com.gxlydlyf.geyserdetector.api.BedrockPlatform;
import com.gxlydlyf.geyserdetector.api.BedrockPlayer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Binary encoding for {@link BedrockMessage}.
 *
 * <p>Both the proxy and the downstream server call into this same class, which is
 * the point of putting the protocol in the shared artifact: the two sides cannot
 * disagree about the wire format.
 *
 * <p>Every frame starts with a version byte so that a mismatched pair of plugin
 * versions is detected and reported rather than silently misparsed.
 */
public final class BedrockCodec {

    /**
     * Frame format:
     * <pre>
     *   byte        protocol version
     *   byte        message type
     *   ...         type-specific body
     * </pre>
     */
    public static final int PROTOCOL_VERSION = 2;

    /** Upper bound on a short field such as a username or version, as a guard against huge allocations. */
    private static final int MAX_STRING_BYTES = 512;

    /**
     * Upper bound on a form or response payload.
     *
     * <p>Much larger than {@link #MAX_STRING_BYTES} because a form carries its whole
     * component tree, but still bounded: an unbounded length would let a single
     * malformed frame claim gigabytes.
     */
    private static final int MAX_PAYLOAD_BYTES = 1024 * 1024;

    private static final byte TYPE_HELLO = 1;
    private static final byte TYPE_SYNC_REQUEST = 2;
    private static final byte TYPE_SYNC_BEGIN = 3;
    private static final byte TYPE_SYNC_END = 4;
    private static final byte TYPE_ADD = 5;
    private static final byte TYPE_REMOVE = 6;
    private static final byte TYPE_SEND_FORM = 7;
    private static final byte TYPE_FORM_RESPONSE = 8;
    private static final byte TYPE_CLOSE_FORM = 9;

    private BedrockCodec() {
    }

    public static byte[] encode(BedrockMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("message cannot be null");
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(PROTOCOL_VERSION);

            // Written as an if-chain rather than a pattern switch: the build targets
            // Java 17, and pattern switches were not finalised until Java 21.
            if (message instanceof BedrockMessage.Hello) {
                out.writeByte(TYPE_HELLO);
            } else if (message instanceof BedrockMessage.SyncRequest) {
                out.writeByte(TYPE_SYNC_REQUEST);
            } else if (message instanceof BedrockMessage.SyncBegin) {
                out.writeByte(TYPE_SYNC_BEGIN);
            } else if (message instanceof BedrockMessage.SyncEnd) {
                out.writeByte(TYPE_SYNC_END);
            } else if (message instanceof BedrockMessage.Remove remove) {
                out.writeByte(TYPE_REMOVE);
                writeUuid(out, remove.uniqueId());
            } else if (message instanceof BedrockMessage.Add add) {
                BedrockPlayer player = add.player();
                out.writeByte(TYPE_ADD);
                writeUuid(out, player.uniqueId());
                writeString(out, player.username());
                writeString(out, player.platform().name());
                writeString(out, player.version());
                writeString(out, player.xuid());
            } else if (message instanceof BedrockMessage.SendForm sendForm) {
                out.writeByte(TYPE_SEND_FORM);
                writeUuid(out, sendForm.playerId());
                writePayload(out, sendForm.formJson());
            } else if (message instanceof BedrockMessage.FormResponse response) {
                out.writeByte(TYPE_FORM_RESPONSE);
                writeUuid(out, response.playerId());
                writePayload(out, response.responseJson());
            } else if (message instanceof BedrockMessage.CloseForm closeForm) {
                out.writeByte(TYPE_CLOSE_FORM);
                writeUuid(out, closeForm.playerId());
            } else {
                throw new IllegalArgumentException("unknown message type: " + message.getClass());
            }

            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            // The only IOException raised below is an oversized payload. The message
            // is named by its type rather than printed wholesale, because these frames
            // can carry a form of several hundred kilobytes.
            throw new IllegalArgumentException(
                    "cannot encode " + message.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static BedrockMessage decode(byte[] data) throws ProtocolException {
        if (data == null || data.length == 0) {
            throw new ProtocolException("empty frame");
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));

            int version = in.readUnsignedByte();
            if (version != PROTOCOL_VERSION) {
                throw new ProtocolException(
                        "protocol version mismatch: peer sent " + version + ", expected " + PROTOCOL_VERSION);
            }

            int type = in.readUnsignedByte();
            BedrockMessage message = switch (type) {
                case TYPE_HELLO -> new BedrockMessage.Hello();
                case TYPE_SYNC_REQUEST -> new BedrockMessage.SyncRequest();
                case TYPE_SYNC_BEGIN -> new BedrockMessage.SyncBegin();
                case TYPE_SYNC_END -> new BedrockMessage.SyncEnd();
                case TYPE_REMOVE -> new BedrockMessage.Remove(readUuid(in));
                case TYPE_ADD -> {
                    UUID uuid = readUuid(in);
                    String username = readString(in);
                    BedrockPlatform platform = BedrockPlatform.fromName(readString(in));
                    String clientVersion = readString(in);
                    String xuid = readString(in);
                    yield new BedrockMessage.Add(
                            new BedrockPlayer(uuid, username, platform, clientVersion, xuid));
                }
                case TYPE_SEND_FORM -> new BedrockMessage.SendForm(readUuid(in), readPayload(in));
                case TYPE_FORM_RESPONSE -> new BedrockMessage.FormResponse(readUuid(in), readPayload(in));
                case TYPE_CLOSE_FORM -> new BedrockMessage.CloseForm(readUuid(in));
                default -> throw new ProtocolException("unknown message type " + type);
            };

            if (in.available() > 0) {
                throw new ProtocolException("trailing bytes after " + message.getClass().getSimpleName());
            }
            return message;
        } catch (EOFException e) {
            throw new ProtocolException("truncated frame", e);
        } catch (IOException e) {
            throw new ProtocolException("malformed frame", e);
        }
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_STRING_BYTES) {
            // Truncating mid-character would corrupt the string, so drop the field instead.
            out.writeShort(0);
            return;
        }
        out.writeShort(encoded.length);
        out.write(encoded);
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length > MAX_STRING_BYTES) {
            throw new IOException("string length " + length + " exceeds limit");
        }
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }

    /**
     * Writes a form or response payload.
     *
     * <p>Unlike {@link #writeString}, a null is encoded as {@code -1} instead of an
     * empty string. That distinction matters for {@link BedrockMessage.FormResponse}:
     * a null payload means the player dismissed the form, which is different from a
     * form that was submitted with no values.
     */
    private static void writePayload(DataOutputStream out, String value) throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > MAX_PAYLOAD_BYTES) {
            throw new IOException("payload of " + encoded.length + " bytes exceeds the "
                    + MAX_PAYLOAD_BYTES + " byte limit");
        }
        out.writeInt(encoded.length);
        out.write(encoded);
    }

    private static String readPayload(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_PAYLOAD_BYTES) {
            throw new IOException("payload length " + length + " out of range");
        }
        byte[] encoded = new byte[length];
        in.readFully(encoded);
        return new String(encoded, StandardCharsets.UTF_8);
    }
}
