package com.app.streaming.handler;

import com.app.streaming.model.BroadcastSink;
import com.app.streaming.model.RoomRegistry;
import com.app.streaming.model.SessionRegistry;
import com.app.streaming.model.StreamingClient;
import com.app.streaming.model.StreamingRoom;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class AdaptiveVideoBroadcastHandler extends BinaryWebSocketHandler {
    private final Logger log = Logger.getLogger(AdaptiveVideoBroadcastHandler.class.getName());

    private final Map<String, ScheduledFuture<?>> reconnectTimers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final long RECONNECT_DEBOUNCE_MS = 200;
    private static final String FORCE_RECONNECT_SIGNAL = "{\"event\":\"FORCE_RECONNECT\"}";
    private static final String STREAM_RESTARTED_SIGNAL = "{\"event\":\"STREAM_RESTARTED\"}";

    private final SessionRegistry sessionRegistry;
    private final BroadcastSink broadcastSink;
    private final RoomRegistry roomRegistry;

    public AdaptiveVideoBroadcastHandler(SessionRegistry sessionRegistry, BroadcastSink broadcastSink, RoomRegistry roomRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.broadcastSink = broadcastSink;
        this.roomRegistry = roomRegistry;
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

    private String extractRoomIdFromPath(String path) {
        if (path == null || !path.contains("/stream/")) {
            throw new IllegalArgumentException("Invalid stream path mapping.");
        }
        // Splitting by "/stream/" gets us whatever identifier follows it
        return path.substring(path.indexOf("/stream/") + "/stream/".length()).split("/")[0];
    }

    private Map<String, String> parseQueryParameters(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        // Transforms "video=false&mic=false" into a clean Map
        return Arrays.stream(query.split("&"))
                .map(param -> param.split("=", 2))
                .filter(pair -> pair.length == 2)
                .collect(Collectors.toMap(
                        pair -> pair[0].trim(),
                        pair -> pair[1].trim(),
                        (existing, replacement) -> existing // Guard against duplicate keys
                ));
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!session.isOpen() || session.getUri() == null) return;

        URI uri = session.getUri();
        if (uri == null) {
            session.close();
            return;
        }
        // 1. Extract Room ID from the URL Path segment
        String path = uri.getPath(); // e.g., "/stream/c56c94bd-9bf1-4b0d-baca-4a0b1626ca31"
        String roomId = extractRoomIdFromPath(path);

        // Check the existence of the given room id
        // 2. Parse Query Parameters into a clean Map
        Map<String, String> queryParams = parseQueryParameters(uri.getQuery());
        
        // 3. Extract media flags matching your URL names ("video" and "mic")
        boolean isCameraEnabled = Boolean.parseBoolean(queryParams.getOrDefault("video", "false"));
        boolean isMicrophoneEnabled = Boolean.parseBoolean(queryParams.getOrDefault("mic", "false"));
        
        // 4. Validate Room Existence
        StreamingRoom room = roomRegistry.findRoom(roomId);
        if (room == null) {
            // Close connection if room doesn't exist to prevent memory leaks or orphaned connections
            session.close(); 
            return;
        }

        // 5. Initialize the newly redesigned StreamingClient
        StreamingClient newClient = new StreamingClient(
            session, 
            roomId, 
            isCameraEnabled, 
            isMicrophoneEnabled
        );
        // Regist new client
        sessionRegistry.registerClient(newClient);
        // Add newClient
        boolean isSuccess = room.addMember(newClient);
        if(!isSuccess) {
            session.close();
            return;
        }
        // Get list of session active camera exclude curren session
        List<StreamingClient> cameras = sessionRegistry.getClientsByRoomId(roomId).stream()
                .filter(client -> client.isCameraEnabled() && !client.getSessionId().equals(session.getId()))
                .toList();
        
        // Camera exists — schedule a forced reconnect (debounced)
        if (!cameras.isEmpty()) {
            cameras.forEach(camera -> {
                scheduleReconnect(camera);
                log.info("[Handler] Viewer joined — reconnect scheduled for camera: "
                    + camera.getSessionId());
            });
        }

        // Determine if cam is active
        if (newClient.isCameraEnabled()) {
            newClient.clearHeaderCache();
            // Broadcast restarted signal
            broadcastSink.sendTextMessage(session.getId(), roomId, new TextMessage(STREAM_RESTARTED_SIGNAL));
        }
        // Logging to terminal
        log.info(
            "\n[Handler] Client: " + newClient.getSessionId() + 
            "\nJoined with camStatus: " + newClient.isCameraEnabled() + 
            "\nSession id: " + session.getId()
        );
    }
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        // Existing signals
        if (FORCE_RECONNECT_SIGNAL.equals(payload) || STREAM_RESTARTED_SIGNAL.equals(payload)) {
            // ... existing handling ...
            return;
        }

        // Config update
        try {
            JsonNode json = objectMapper.readTree(payload);
            if ("CONFIG_UPDATE".equals(json.path("type").asString())) {
                boolean video = json.path("video").asBoolean(false);
                boolean mic   = json.path("mic").asBoolean(false);

                String roomId = extractRoomIdFromPath(session.getUri().getPath());
                StreamingRoom room = roomRegistry.findRoom(roomId);
                if (room == null) return;

                StreamingClient client = room.getMemberById(session.getId());
                if (client == null) return;

                client.setCameraEnabled(video);
                client.setMicrophoneEnabled(mic);

                log.info("[Handler] Config updated for " + session.getId() 
                    + " — video=" + video + " mic=" + mic);
            }
        } catch (Exception e) {
            log.warning("[Handler] Failed to parse text message: " + payload);
        }
    }
    
    @Override
    public void handleBinaryMessage(WebSocketSession session,
                                    @NonNull BinaryMessage message) throws Exception {

        StreamingClient client = sessionRegistry.getClientById(session.getId());
        // Guard 1: Safety check if client registry isn't fully ready yet
        if (client == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if(!client.isCameraEnabled())
            return;

        // Guard 3: Validate Room Presence securely
        StreamingRoom room = roomRegistry.findRoom(client.getRoomId());
        if (room == null || !room.isMemberPresent(client.getSessionId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        
        // Payload length must greater than 8
        ByteBuffer payload = message.getPayload();
        if (payload.remaining() <= 8) {
            log.warning("[Handler] Payload too short, dropping: " + session.getId());
            return;
        }
        byte[] raw = new byte[payload.remaining()];
        payload.get(raw);
        // Caching header
        if(client.getInitializationHeaderSegment() == null) {
            client.setInitializationHeaderSegment(raw);
        }

        // 3. Broadcast ALWAYS runs, even if detector failed
        broadcastSink.sendBinaryMessage(session.getId(), room.getId(), new BinaryMessage(raw));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String sessionId = session.getId();
        // 1. Fetch first, THEN remove to fix the NullPointerException crash
        StreamingClient client = sessionRegistry.getClientById(sessionId);
        sessionRegistry.removeSession(sessionId);

        if (client == null) {
            log.warning("[Handler] Connection closed for untracked session: " + sessionId);
            return;
        }

        // 2. Clean up room resources safely using optional/defensive null protection
        StreamingRoom room = roomRegistry.findRoom(client.getRoomId());
        if (room != null) {
            room.removeMember(sessionId);
            
            // Auto-destroy empty rooms to prevent memory accumulation leaks
            if (room.getCurrentSize() == 0) {
                roomRegistry.removeRoom(room);
                log.info("[Handler] Empty room destroyed cleanly: " + room.getId());
            }
        }
        log.info(String.format("[Handler] Connection closed: %s | Reason code: %d (%s)", 
                sessionId, status.getCode(), status.getReason()));
    }
}
