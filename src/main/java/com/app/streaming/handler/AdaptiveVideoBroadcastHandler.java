package com.app.streaming.handler;

import com.app.streaming.service.VideoTranscodingService;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AdaptiveVideoBroadcastHandler extends BinaryWebSocketHandler {
    // Session lists
//    private List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private List<WebSocketSession> highResViewer = new CopyOnWriteArrayList<WebSocketSession>();
    private List<WebSocketSession> lowResViewer = new CopyOnWriteArrayList<WebSocketSession>();

    // Key
    private static final String ROLE_KEY = "ROLE";
    private static final String PROFILE_KEY = "PROFILE";
    // Initialize header
    private byte[] initHeaderSegment = null;
//    private static final String FILE_STREAM_KEY = "FILE_OUTPUT_STREAM";
    private volatile byte[] latestKeyframeCluster = null;

    // ADD: Jackson for JSON, or just build the string manually
    private static final String STREAM_RESTARTED_SIGNAL = "{\"event\":\"STREAM_RESTARTED\"}";

    // WebM keyframe cluster magic bytes: starts with 0x1F43B675
    private boolean isKeyframeCluster(byte[] data) {
        if (data.length < 12) return false; // need room for timestamp header too
        // Skip the 8-byte timestamp prefix your client strips
        // Check offset 8 for WebM Cluster EBML ID
        return (data[8] & 0xFF) == 0x1F &&
                (data[9] & 0xFF) == 0x43 &&
                (data[10] & 0xFF) == 0xB6 &&
                (data[11] & 0xFF) == 0x75;
    }

    // ADD THIS helper
    private void broadcastTextToViewers(String json) {
        TextMessage signal = new TextMessage(json);
        for (WebSocketSession viewer : highResViewer) {
            if (viewer.isOpen()) {
                try {
                    viewer.sendMessage(signal);
                    System.out.println("Signal sent to viewer: " + viewer.getId()); // ADD THIS
                } catch (Exception e) {
                    System.err.println("Failed to notify viewer: " + e.getMessage());
                }
            }
        }
//        for (WebSocketSession viewer : lowResViewer) {
//            if (viewer.isOpen()) {
//                try {
//                    viewer.sendMessage(signal);
//                } catch (Exception e) {
//                    System.err.println("Failed to notify viewer: " + e.getMessage());
//                }
//            }
//        }
    }

    private VideoTranscodingService transcoder;

    public AdaptiveVideoBroadcastHandler() {
        try {
            // Initialize the transcoder and give it the reference to the low-res pool
            this.transcoder = new VideoTranscodingService(lowResViewer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
//        // Add new session after a new connection was established
        String query = session.getUri().getQuery();
        String role = (query != null && query.contains("role=viewer")) ? "VIEWER" : "CAMERA";
        String profile = (query != null && query.contains("profile=low")) ? "LOW" : "HIGH";
        System.out.println("sessionId: " + session.getId() + "role: " + role + " - profile: "+profile);


        if("CAMERA".equals(role)) {
            // Reset cache when a new camera segment connect
            initHeaderSegment = null;
            latestKeyframeCluster = null;

            // ADD THIS: notify existing viewers to hard reset their pipeline
            // Your client-side STREAM_RESTARTED handler already handles this!
            broadcastTextToViewers(STREAM_RESTARTED_SIGNAL);

            System.out.println("Camera connected successfully: " + session.getId());
        } else {
            if("HIGH".equals(profile))
                highResViewer.add(session);
            else
                lowResViewer.add(session);
            try {
                System.out.println("Viewer join stream: " + session.getId());
                if(initHeaderSegment != null) {
                    // Send init header to viewer
                    session.sendMessage(new BinaryMessage(initHeaderSegment));
                    System.out.println("Sent cached streaming initialization header to late viewer: " + session.getId());
                }
                if (latestKeyframeCluster != null) {
                    // Send latest keyframe to late viewer
                    session.sendMessage(new BinaryMessage(latestKeyframeCluster));
                    System.out.println("Sent latest keyframe cluster to late viewer: " + session.getId());
                }
            } catch (Exception ex) {
                System.err.println("Failed to send catch-up data to viewer: " + ex.getMessage());
            }

        }
        // Save role and profile to current session
        session.getAttributes().put(ROLE_KEY, role);
        session.getAttributes().put(PROFILE_KEY, profile);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if(!session.getAttributes().get(ROLE_KEY).equals("CAMERA"))
            return;

        // Get the byte buffer from the message payload
        java.nio.ByteBuffer payload = message.getPayload();
        if(payload.remaining() < 8 ) {
            System.out.println("Broken payload");
            return;
        } // Safe's guard against the broken payload

        // Get the byte arrays
        byte[] bytes = new byte[payload.remaining()];
        // Transfer bytes from buffer into array
        payload.get(bytes);

        // If this is absolute chunk to the camera, save to the config
        if(initHeaderSegment == null) {
            initHeaderSegment = bytes;
            System.out.println("Captured the initialization of the header segment: " + session.getId());
        }
        // viewers always get a recent starting point
        if (isKeyframeCluster(bytes)) {
            latestKeyframeCluster = bytes;
        }

        for(WebSocketSession viewerSession : highResViewer) {
            // Condition allow to only stream to others
            if(viewerSession.isOpen() && !viewerSession.getId().equals(session.getId())) {
                viewerSession.sendMessage(new BinaryMessage(bytes));
            }
        }

        // Feed the chunk into the FFmpeg engine to be downscaled for the low-res pool!
        if(transcoder != null) {
            transcoder.feedRawHighChunk(bytes);
        }
    }

    @Override
    public void afterConnectionClosed(
            WebSocketSession session,
            org.springframework.web.socket.CloseStatus status
    ) {
        // Remove session after someone leave
        if(session.getAttributes().get(PROFILE_KEY).equals("HIGH"))
            highResViewer.remove(session);
        else if(session.getAttributes().get(PROFILE_KEY).equals("LOW")) {
            lowResViewer.remove(session);
        }

        if(session.getAttributes().get(ROLE_KEY).equals("CAMERA")) {
            System.out.println("Flush the camera header segment: " + session.getId());
            initHeaderSegment = null; // Flush the camera's initial header segment
            latestKeyframeCluster = null; // also flush this
            // ADD THIS: tell all viewers the stream is dead
            // so they can show a "reconnecting..." UI instead of freezing
            broadcastTextToViewers(STREAM_RESTARTED_SIGNAL);

            System.out.println("Camera disconnected, flushed header, notified viewers");
        }
        System.out.println(
                "Connection closed for session: " + session.getId() +
                        "|" + "Reason: " + status.getReason()
        );
    }
}
