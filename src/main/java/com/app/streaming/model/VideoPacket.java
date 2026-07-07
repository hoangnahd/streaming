package com.app.streaming.model;
/**
 * Represents a single WebSocket binary message from the camera.
 * Separates the 8-byte timestamp header from the WebM payload.
 */
public final class VideoPacket {

    private static final int TIMESTAMP_BYTES = 8;

    private final long   timestamp;
    private final byte[] webmData;

    private VideoPacket(long timestamp, byte[] webmData) {
        this.timestamp = timestamp;
        this.webmData  = webmData;
    }

    /**
     * Parses a raw WebSocket message into a VideoPacket.
     * First 8 bytes = timestamp (big-endian double → long millis).
     * Remaining bytes = raw WebM data.
     */
    public static VideoPacket parse(byte[] raw) {
        if (raw.length <= TIMESTAMP_BYTES) {
            throw new IllegalArgumentException("Payload too short: " + raw.length);
        }
        // Extract the timestamp
        double tsDouble = java.nio.ByteBuffer.wrap(raw, 0, TIMESTAMP_BYTES).getDouble();
        long timestamp = (long)tsDouble;

        byte[] webm = new byte[raw.length - TIMESTAMP_BYTES];
        System.arraycopy(raw, TIMESTAMP_BYTES, webm, 0, webm.length);

        return new VideoPacket(timestamp, webm);
    }

    // /**
    //  * Prepends the timestamp back onto transcoded bytes for viewer delivery.
    //  */
    // public byte[] toViewerPayload(byte[] transcodedWebm) {
    //     java.nio.ByteBuffer out = java.nio.ByteBuffer.allocate(8 + transcodedWebm.length);
    //     out.putDouble((double) timestamp);
    //     out.put(transcodedWebm);
    //     return out.array();
    // }

    public long   getTimestamp() { return timestamp; }
    public byte[] getWebmData()  { return webmData;  }
}