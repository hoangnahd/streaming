package com.app.streaming.transcoder;

import java.io.ByteArrayOutputStream;

/**
 * Detects WebM cluster boundaries across arbitrary chunk boundaries using a
 * 3-byte carry-over window. Accumulates cluster bytes and notifies a listener
 * when a complete keyframe cluster is ready.
 *
 * Thread-safety: NOT thread-safe. Caller must ensure single-threaded feed().
 */
public class KeyframeDetector {

    // WebM Cluster EBML ID
    private static final byte[] CLUSTER_ID = {0x1F, 0x43, (byte) 0xB6, 0x75};

    // Max cluster buffer size — discard if a "cluster" grows beyond this
    // (guards against false positives / corrupted streams)
    private static final int MAX_CLUSTER_BYTES = 512 * 1024; // 512 KB

    private final KeyframeListener listener;

    // Carry-over: last 3 bytes of previous chunk, to detect IDs split across boundary
    private final byte[] carryOver = new byte[3];
    private int carryOverLen = 0;

    // Accumulation buffer for the current cluster being assembled
    private ByteArrayOutputStream currentCluster = null;
    // Absolute byte offset where currentCluster started (for logging/debug)
    private boolean inCluster = false;

    public KeyframeDetector(KeyframeListener listener) {
        this.listener = listener;
    }

    /**
     * Feed the next raw chunk from the camera WebSocket.
     * Called on every incoming binary message — cheap path when no cluster boundary found.
     */
    public void feed(byte[] raw) {
        byte[] window = buildWindow(raw);
        int searchOffset = 0;

        while (true) {
            int hitInWindow = indexOf(window, CLUSTER_ID, searchOffset);
            if (hitInWindow == -1) break;

            int hitInRaw = hitInWindow - carryOverLen;
            // System.out.println("Cluster ID found at window offset " + hitInWindow 
            //     + " (raw offset " + hitInRaw + "), inCluster=" + inCluster);
            // If we were accumulating a previous cluster, finalize it now
            if (inCluster && currentCluster != null) {
                // Append bytes up to this new cluster start
                int endInRaw = Math.max(0, hitInRaw);
                if (endInRaw > 0) {
                    currentCluster.write(raw, 0, endInRaw);
                }
                finalizeCluster(currentCluster.toByteArray());
            }

            // Start accumulating the new cluster
            currentCluster = new ByteArrayOutputStream();
            inCluster = true;

            // Write from the cluster ID start in raw onwards (may be negative if ID straddles boundary)
            int startInRaw = Math.max(0, hitInRaw);
            if (hitInRaw < 0) {
                // Part of the ID came from carryOver — write the full ID first
                currentCluster.write(CLUSTER_ID, 0, CLUSTER_ID.length);
                // Then write the rest of raw from after the ID portion that's in raw
                int alreadyWritten = CLUSTER_ID.length + hitInRaw; // hitInRaw is negative
                int rawStart = Math.max(0, alreadyWritten);
                if (rawStart < raw.length) {
                    currentCluster.write(raw, rawStart, raw.length - rawStart);
                }
            } else {
                currentCluster.write(raw, startInRaw, raw.length - startInRaw);
            }

            searchOffset = hitInWindow + CLUSTER_ID.length;
        }

        // No new cluster boundary found — if we're accumulating, append the whole chunk
        if (inCluster && currentCluster != null && searchOffset == 0) {
            currentCluster.write(raw, 0, raw.length);
            if (currentCluster.size() > MAX_CLUSTER_BYTES) {
                // Oversize — likely a false positive or corrupted stream; discard
                currentCluster = null;
                inCluster = false;
            }
        }

        // Update carry-over: last 3 bytes of raw
        updateCarryOver(raw);
    }

    /**
     * Call this when the stream resets (camera reconnects).
     * Discards all buffered state.
     */
    public void reset() {
        carryOverLen = 0;
        currentCluster = null;
        inCluster = false;
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private byte[] buildWindow(byte[] raw) {
        byte[] window = new byte[carryOverLen + raw.length];
        System.arraycopy(carryOver, 0, window, 0, carryOverLen);
        System.arraycopy(raw, 0, window, carryOverLen, raw.length);
        return window;
    }

    private void updateCarryOver(byte[] raw) {
        int len = Math.min(3, raw.length);
        System.arraycopy(raw, raw.length - len, carryOver, 0, len);
        carryOverLen = len;
    }

    private void finalizeCluster(byte[] clusterBytes) {
        // System.out.println("finalizeCluster called, size: " + clusterBytes.length);
        
        // Dump first 80 bytes as hex to see the actual structure
        StringBuilder hex = new StringBuilder("First 80 bytes: ");
        for (int i = 0; i < Math.min(80, clusterBytes.length); i++) {
            hex.append(String.format("%02X ", clusterBytes[i]));
        }
        // System.out.println(hex);
        
        // Also scan and report every 0xA0, 0xA1, 0xA3 found and their position
        // System.out.println("Block-related IDs found:");
        for (int i = 0; i < clusterBytes.length - 4; i++) {
            int b = clusterBytes[i] & 0xFF;
            if (b == 0xA3 || b == 0xA1 || b == 0xA0) {
                // Print the next 6 bytes after the ID
                StringBuilder ctx = new StringBuilder();
                for (int j = i; j < Math.min(i + 7, clusterBytes.length); j++) {
                    ctx.append(String.format("%02X ", clusterBytes[j]));
                }
                // System.out.printf("  offset %d: %s%n", i, ctx);
            }
        }
        
        boolean isKeyframe = isKeyframeCluster(clusterBytes);
        // System.out.println("isKeyframeCluster result: " + isKeyframe);
        if (isKeyframe) {
            listener.onKeyframeFound(clusterBytes);
        }
    }

    /**
     * Lightweight keyframe check: find first SimpleBlock (0xA3) and check
     * flags byte bit 7. Does NOT do full EBML parsing.
     */
    private boolean isKeyframeCluster(byte[] cluster) {
        int i = 0;

        // Skip Cluster ID (4 bytes)
        if (cluster.length < 5) return false;
        i = 4;

        // Skip cluster size varint — but handle the "unknown size" sentinel
        int clusterSizeLen = ebmlSizeLength(cluster, i);
        long clusterSize = ebmlSizeValue(cluster, i);
        i += clusterSizeLen;
        // If unknown size (all data bits = 1), just parse until end of buffer
        boolean unknownSize = isUnknownSize(cluster, i - clusterSizeLen, clusterSizeLen);

        // Parse elements sequentially
        while (i < cluster.length - 4) {
            int elementId = cluster[i] & 0xFF;

            if (elementId == 0xE7) {
                // Timecode — skip
                if (i + 1 >= cluster.length) break;
                int sizeLen = ebmlSizeLength(cluster, i + 1);
                long dataLen = ebmlSizeValue(cluster, i + 1);
                if (isUnknownSize(cluster, i + 1, sizeLen) || dataLen > cluster.length) break;
                i += 1 + sizeLen + (int) dataLen;
                continue;
            }

            if (elementId == 0xA7) {
                // Position element — skip
                if (i + 1 >= cluster.length) break;
                int sizeLen = ebmlSizeLength(cluster, i + 1);
                long dataLen = ebmlSizeValue(cluster, i + 1);
                if (isUnknownSize(cluster, i + 1, sizeLen) || dataLen > cluster.length) break;
                i += 1 + sizeLen + (int) dataLen;
                continue;
            }

            if (elementId == 0xAB) {
                // PrevSize element — skip
                if (i + 1 >= cluster.length) break;
                int sizeLen = ebmlSizeLength(cluster, i + 1);
                long dataLen = ebmlSizeValue(cluster, i + 1);
                if (isUnknownSize(cluster, i + 1, sizeLen) || dataLen > cluster.length) break;
                i += 1 + sizeLen + (int) dataLen;
                continue;
            }

            if (elementId == 0xA3) {
                // SimpleBlock
                if (i + 1 >= cluster.length) break;
                int sizeLen = ebmlSizeLength(cluster, i + 1);
                long dataLen = ebmlSizeValue(cluster, i + 1);
                if (isUnknownSize(cluster, i + 1, sizeLen)) break;

                // flagsPos = after ID(1) + sizeLen + trackNumber varint(1+) + timecode(2)
                int trackPos = i + 1 + sizeLen;
                if (trackPos >= cluster.length) break;
                int trackLen = varintLength(cluster, trackPos);
                int flagsPos = trackPos + trackLen + 2;

                if (flagsPos < cluster.length) {
                    return (cluster[flagsPos] & 0x80) != 0; // bit 7 = keyframe
                }
                return false;
            }

            if (elementId == 0xA0) {
                // BlockGroup — keyframe if no ReferenceBlock (0xFB) inside
                if (i + 1 >= cluster.length) break;
                int sizeLen = ebmlSizeLength(cluster, i + 1);
                long dataLen = ebmlSizeValue(cluster, i + 1);
                if (isUnknownSize(cluster, i + 1, sizeLen) || dataLen > cluster.length) break;

                int bgStart = i + 1 + sizeLen;
                int bgEnd = (int) Math.min(bgStart + dataLen, cluster.length);
                return !containsByte(cluster, bgStart, bgEnd - bgStart, (byte) 0xFB);
            }

            // Unknown element — skip using its declared size
            if (i + 1 >= cluster.length) break;
            int sizeLen = ebmlSizeLength(cluster, i + 1);
            long dataLen = ebmlSizeValue(cluster, i + 1);
            // If unknown size or absurdly large, we can't skip — stop parsing
            if (isUnknownSize(cluster, i + 1, sizeLen) || dataLen > cluster.length) break;
            i += 1 + sizeLen + (int) dataLen;
        }

        return false;
    }

    /**
     * Checks if the varint at the given offset represents the WebM "unknown size" sentinel
     * (all data bits set to 1, e.g. 0xFF for 1-byte, 0x7FFF for 2-byte, etc.)
     */
    private boolean isUnknownSize(byte[] data, int offset, int varintLen) {
        if (offset + varintLen > data.length) return true;
        // The sentinel has all data bits = 1.
        // For a 1-byte varint: 0xFF. For 2-byte: 0x7F 0xFF. Etc.
        // Easiest check: decoded value equals (2^(7*varintLen) - 1)
        long decoded = ebmlSizeValue(data, offset);
        long sentinel = (1L << (7L * varintLen)) - 1L;
        return decoded == sentinel;
    }

    private int ebmlSizeLength(byte[] data, int offset) {
        if (offset >= data.length) return 1;
        int first = data[offset] & 0xFF;
        if ((first & 0x80) != 0) return 1;
        if ((first & 0x40) != 0) return 2;
        if ((first & 0x20) != 0) return 3;
        if ((first & 0x10) != 0) return 4;
        if ((first & 0x08) != 0) return 5;
        if ((first & 0x04) != 0) return 6;
        if ((first & 0x02) != 0) return 7;
        if ((first & 0x01) != 0) return 8;
        return 1; // malformed
    }

    private long ebmlSizeValue(byte[] data, int offset) {
        int len = ebmlSizeLength(data, offset);
        if (offset + len > data.length) return 0;
        long value = data[offset] & 0xFF;
        // Mask off the leading width bit
        value &= (0xFFL >> len);
        for (int i = 1; i < len; i++) {
            value = (value << 8) | (data[offset + i] & 0xFF);
        }
        return value;
    }

    private int varintLength(byte[] data, int offset) {
        if (offset >= data.length) return 1;
        int first = data[offset] & 0xFF;
        if ((first & 0x80) != 0) return 1;
        if ((first & 0x40) != 0) return 2;
        if ((first & 0x20) != 0) return 3;
        if ((first & 0x10) != 0) return 4;
        return 1;
    }

    private boolean containsByte(byte[] data, int from, int length, byte target) {
        int end = Math.min(from + length, data.length);
        for (int i = from; i < end; i++) {
            if (data[i] == target) return true;
        }
        return false;
    }

    /** Boyer-Moore-Horspool style scan — O(N) with early exit. */
    private int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
