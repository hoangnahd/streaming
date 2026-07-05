package com.app.streaming.transcoder;

import com.app.streaming.broadcast.BroadcastSink;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates and tracks one CameraTranscoder per camera session.
 * Ensures clean teardown when a session ends.
 */
@Component
public class TranscoderFactory {

    private final ConcurrentHashMap<String, CameraTranscoder> active = new ConcurrentHashMap<>();
    private final BroadcastSink broadcastSink;

    public TranscoderFactory(BroadcastSink broadcastSink) {
        this.broadcastSink = broadcastSink;
    }

    public CameraTranscoder getOrCreate(String sessionId) throws IOException {
        return active.computeIfAbsent(sessionId, id -> {
            try {
                return CameraTranscoder.start(broadcastSink, id);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public void shutdown(String sessionId) {
        CameraTranscoder transcoder = active.remove(sessionId);
        if (transcoder != null) {
            transcoder.close();
        }
    }
}