package com.app.streaming.handler;

import com.app.streaming.broadcast.BroadcastSink;
import com.app.streaming.session.SessionRegistry;
import com.app.streaming.session.StreamingClient;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.logging.Logger;

@Component
public class AdaptiveVideoBroadcastHandler extends BinaryWebSocketHandler {
    private final Logger log = Logger.getLogger(AdaptiveVideoBroadcastHandler.class.getName());

    private static final String ROLE_KEY = "ROLE";
    private static final String PROFILE_KEY = "PROFILE";
    private static final String STREAM_RESTARTED_SIGNAL = "{\"event\":\"STREAM_RESTARTED\"}";

    private final SessionRegistry sessionRegistry;
    private final BroadcastSink broadcastSink;
    // private final TranscoderFactory transcoderFactory;


    public AdaptiveVideoBroadcastHandler(SessionRegistry sessionRegistry, BroadcastSink broadcastSink) {
        this.sessionRegistry = sessionRegistry;
        this.broadcastSink = broadcastSink;
        // this.transcoderFactory = transcoderFactory;
    }

    // WebM keyframe cluster magic bytes: starts with 0x1F43B675
    private boolean isKeyframeCluster(byte[] webmData) {
        if (webmData.length < 12) return false;
        return (webmData[8] & 0xFF) == 0x1F &&
            (webmData[9] & 0xFF) == 0x43 &&
            (webmData[10] & 0xFF) == 0xB6 &&
            (webmData[11] & 0xFF) == 0x75;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("ENTER afterConnectionEstablished");

        if (!session.isOpen() || session.getUri() == null) return;

        String query   = session.getUri().getQuery();
        String role    = (query != null && query.contains("role=viewer")) ? "VIEWER" : "CAMERA";
        String profile = (query != null && query.contains("profile=low")) ? "LOW"    : "HIGH";

        log.info("sessionId: " + session.getId() + " role: " + role + " profile: " + profile);

        session.getAttributes().put(ROLE_KEY, role);
        session.getAttributes().put(PROFILE_KEY, profile);

        StreamingClient newClient = new StreamingClient(session, profile, role);
        sessionRegistry.registerClient(newClient);
        log.info("[Handler] Client registered: " + session.getId());

        if ("CAMERA".equals(role)) {
            newClient.setInitHeaderSegment(null);
            newClient.setLatestKeyframeCluster(null);

            broadcastSink.sendTextMessage(session.getId(), new TextMessage(STREAM_RESTARTED_SIGNAL));
            log.info("[Handler] Camera connected successfully: " + session.getId());
        } else {
            log.info("[Handler] Viewer joined stream: " + session.getId());
            broadcastSink.sendInitHeaders(session);
            broadcastSink.sendLatestKeyFrames(session);
        }
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session,
                                    @NonNull BinaryMessage message) throws Exception {

        if (!"CAMERA".equals(session.getAttributes().get(ROLE_KEY))) return;

        ByteBuffer payload = message.getPayload();
        if (payload.remaining() <= 8) {
            log.warning("[Handler] Payload too short, dropping: " + session.getId());
            return;
        }

        byte[] raw = new byte[payload.remaining()];
        payload.get(raw);

        // // 1. Parse — separate timestamp from WebM data
        // VideoPacket packet = VideoPacket.parse(raw);

        StreamingClient client = sessionRegistry.getClientById(session.getId());
        if(client.getInitHeaderSegment() == null) {
            client.setInitHeaderSegment(raw);
        }
        if(isKeyframeCluster(raw)) {
            client.setLatestKeyframeCluster(raw);
        }

        // 4. Route based on profile
        // if ("HIGH".equals(client.getProfile())) {
        //     // Transcode path — feed WebM only to FFmpeg.
        //     // Timestamp is preserved inside the packet and re-attached
        //     // to the transcoded output by CameraTranscoder's stdout reader.
        //     CameraTranscoder transcoder = transcoderFactory.getOrCreate(session.getId());
        //     transcoder.feedPacket(packet);
        // } else {
            // Direct broadcast path — forward raw bytes including timestamp
        broadcastSink.sendBinaryMessage(session.getId(), new BinaryMessage(raw));
        //}
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session,
                                      @NonNull CloseStatus status) {

        sessionRegistry.removeSession(session.getId());

        if ("CAMERA".equals(session.getAttributes().get(ROLE_KEY))) {
            log.info("[Handler] Flushing camera header segment: " + session.getId());

            broadcastSink.sendTextMessage(session.getId(), new TextMessage(STREAM_RESTARTED_SIGNAL));
            log.info("[Handler] Camera disconnected, viewers notified: " + session.getId());

            // if ("HIGH".equals(session.getAttributes().get(PROFILE_KEY))) {
            //     log.info("[Handler] Closing transcoding process: " + session.getId());
            //     // Flushes stdin, waits for FFmpeg to drain remaining frames,
            //     // then force-kills if it doesn't exit within the timeout.
            //     transcoderFactory.shutdown(session.getId());
            // }
        }

        log.info("[Handler] Connection closed: " + session.getId() +
                " | Reason: " + status.getReason());
    }
}
