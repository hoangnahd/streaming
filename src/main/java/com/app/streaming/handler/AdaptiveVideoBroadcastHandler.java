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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Component
public class AdaptiveVideoBroadcastHandler extends BinaryWebSocketHandler {
    private final Logger log = Logger.getLogger(AdaptiveVideoBroadcastHandler.class.getName());

    private final Map<String, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final long RECONNECT_DEBOUNCE_MS = 500;
    private static final String FORCE_RECONNECT_SIGNAL = "{\"event\":\"FORCE_RECONNECT\"}";
    private static final String CAM_STATUS = "CAM_STATUS";
    private static final String PROFILE_KEY = "PROFILE";
    private static final String STREAM_RESTARTED_SIGNAL = "{\"event\":\"STREAM_RESTARTED\"}";

    private final SessionRegistry sessionRegistry;
    private final BroadcastSink broadcastSink;



    public AdaptiveVideoBroadcastHandler(SessionRegistry sessionRegistry, BroadcastSink broadcastSink) {
        this.sessionRegistry = sessionRegistry;
        this.broadcastSink = broadcastSink;
    }


    private void scheduleReconnect(StreamingClient camera) {
        String cameraId = camera.getSessionId();

        // Cancel existing timer — reset the debounce window
        cancelReconnectTimer(cameraId);

        ScheduledFuture<?> future = scheduler.schedule(() -> {
            reconnectTimers.remove(cameraId);
            WebSocketSession camSession = camera.getSession();
            if (camSession != null && camSession.isOpen()) {
                try {
                    log.info("[Handler] Forcing camera reconnect: " + cameraId);
                    camSession.sendMessage(new TextMessage(FORCE_RECONNECT_SIGNAL));
                } catch (IOException e) {
                    log.warning("[Handler] Failed to send FORCE_RECONNECT: " + e.getMessage());
                }
            }
        }, RECONNECT_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        
        reconnectTimers.put(cameraId, future);
    }

    private void cancelReconnectTimer(String cameraId) {
        ScheduledFuture<?> existing = reconnectTimers.remove(cameraId);
        if (existing != null) {
            existing.cancel(false);
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!session.isOpen() || session.getUri() == null) return;

        String query   = session.getUri().getQuery();
        boolean isCamActive    = (query != null && query.contains("isCamActive=true")) ? true : false;
        String profile = (query != null && query.contains("profile=low")) ? "LOW"    : "HIGH";

        session.getAttributes().put(CAM_STATUS, isCamActive);
        session.getAttributes().put(PROFILE_KEY, profile);

        StreamingClient newClient = new StreamingClient(session, profile, isCamActive);
        sessionRegistry.registerClient(newClient);
        log.info("[Handler] Client registered: " + session.getId());

        if (isCamActive) {
            newClient.clearCache();
            // Broadcast restarted signal
            broadcastSink.sendTextMessage(session.getId(), new TextMessage(STREAM_RESTARTED_SIGNAL));
        
        } else {
            List<StreamingClient> cameras = sessionRegistry.getClients().stream()
                .filter(client -> client.getIsCamActive())          // Boolean unboxing, no need for == true
                .toList();;
            // Camera exists — schedule a forced reconnect (debounced)
            if (!cameras.isEmpty()) {
                cameras.forEach(camera -> {
                    scheduleReconnect(camera);
                    log.info("[Handler] Viewer joined — reconnect scheduled for camera: "
                        + camera.getSessionId());
                });
            }
        }

        log.info(
            "\n[Handler] Client: " + newClient.getSessionId() + 
            "\nJoined with camStatus: " + isCamActive + 
            "\nSession id: " + session.getId()
        );
    }

    @Override
    public void handleBinaryMessage(WebSocketSession session,
                                    @NonNull BinaryMessage message) throws Exception {

        if (session.getAttributes().get(CAM_STATUS).equals(false)) return;

        ByteBuffer payload = message.getPayload();
        if (payload.remaining() <= 8) {
            log.warning("[Handler] Payload too short, dropping: " + session.getId());
            return;
        }

        byte[] raw = new byte[payload.remaining()];
        payload.get(raw);

        StreamingClient client = sessionRegistry.getClientById(session.getId());
        if(client.getInitHeaderSegment() == null) {
            client.setInitHeaderSegment(raw);
        }

        // 3. Broadcast ALWAYS runs, even if detector failed
        broadcastSink.sendBinaryMessage(session.getId(), new BinaryMessage(raw));
        
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {

        sessionRegistry.removeSession(session.getId());

        if (session.getAttributes().get(CAM_STATUS).equals(true)) {
            log.info("\n[Handler] Flushing camera header segment: " + session.getId());

            broadcastSink.sendTextMessage(session.getId(), new TextMessage(STREAM_RESTARTED_SIGNAL));
            log.info("\n[Handler] Camera disconnected, viewers notified: " + session.getId());
        }

        log.info("\n[Handler] Connection closed: " + session.getId() +
                " | Reason: " + status.getReason());
    }
}
