package com.app.streaming.handler;

import com.app.streaming.DTO.ClientInfo;
import com.app.streaming.model.StreamingClient;
import com.app.streaming.model.StreamingRoom;
import com.app.streaming.registry.RoomRegistry;
import com.app.streaming.registry.SessionRegistry;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Component
public class AdaptiveVideoBroadcastHandler extends BinaryWebSocketHandler {
    private final Logger log = Logger.getLogger(AdaptiveVideoBroadcastHandler.class.getName());

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String FORCE_RECONNECT_SIGNAL = "{\"event\":\"FORCE_RECONNECT\"}";
    private static final String STREAM_RESTARTED_SIGNAL = "{\"event\":\"FORCE_RESTARTED\"}";

    private final SessionRegistry sessionRegistry;
    private final BroadcastSink broadcastSink;
    private final RoomRegistry roomRegistry;

    public AdaptiveVideoBroadcastHandler(SessionRegistry sessionRegistry, BroadcastSink broadcastSink, RoomRegistry roomRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.broadcastSink = broadcastSink;
        this.roomRegistry = roomRegistry;
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
    public void afterConnectionEstablished(WebSocketSession rawSession) throws Exception {
        WebSocketSession session = new ConcurrentWebSocketSessionDecorator(rawSession, 5000 /* send timeout ms */, 65536 /* buffer size bytes */);
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
        StreamingClient newClient = new StreamingClient(session, roomId, isCameraEnabled, isMicrophoneEnabled);
         // Register new client
        sessionRegistry.registerClient(newClient);
        boolean isSuccess = room.addMember(newClient);
        if (!isSuccess) { session.close(); return; }

        // 1. SESSION_ID first
        session.sendMessage(new TextMessage(
            objectMapper.writeValueAsString(Map.of("type", "SESSION_ID", "id", session.getId()))
        ));

        // 2. Participant list to ALL — this renders cards on every client
        List<ClientInfo> allClients = room.getAllClients().stream().map(ClientInfo::new).toList();
        broadcastSink.broadcastTextMessage("none", roomId,
            new TextMessage(objectMapper.writeValueAsString(allClients)));

        // 3. Replay cached init segments to the new client ONLY
        //    TCP ordering guarantees this arrives after the participant list above
        List<StreamingClient> existingCameras = room.getAllClients().stream()
            .filter(c -> !c.getId().equals(newClient.getId()))
            .filter(StreamingClient::isCameraEnabled)
            .toList();

        for (StreamingClient camera : existingCameras) {
            camera.getSession().sendMessage(new TextMessage(
                objectMapper.writeValueAsString(Map.of("event", "FORCE_RESTARTED"))
            ));
            log.info("[Handler] Sent restart signal to " + camera.getId() + " due to new joiner " + newClient.getId());
        }
    }
// Remove scheduleStreamResetForClients — no longer needed
    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();

        // Existing signals
        if (FORCE_RECONNECT_SIGNAL.equals(payload) || STREAM_RESTARTED_SIGNAL.equals(payload)) {
            return;
        }

        // Config update
        try {
            JsonNode json = objectMapper.readTree(payload);
            if ("CONFIG_UPDATE".equals(json.path("type").asString())) {
                boolean video = json.path("video").asBoolean(false);
                boolean mic   = json.path("mic").asBoolean(false);

                StreamingClient client = sessionRegistry.getClientById(session.getId());
                if (client == null) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }

                String roomId = client.getRoomId();
                StreamingRoom room = roomRegistry.findRoom(roomId);
                if (room == null) {
                    session.close(CloseStatus.POLICY_VIOLATION);
                    return;
                }
                
                // Update config
                client.setCameraEnabled(video);
                client.setMicrophoneEnabled(mic);

                // Get list of session clients and active camera
                List<ClientInfo> clients = room.getAllClients().stream().map(ClientInfo::new).toList();

                String jsonPayload = objectMapper.writeValueAsString(clients);
                broadcastSink.broadcastTextMessage("none", roomId, new TextMessage(jsonPayload));

                log.info("[Handler] Config updated for " + session.getId() 
                    + " — video=" + video + " mic=" + mic);
            }
        } catch (Exception e) {
            log.warning("[Handler] Failed to parse text message: " + payload);
        }
    }
    
    private final ConcurrentHashMap<String, byte[]> initSegmentCache = new ConcurrentHashMap<>();
    private static final byte[] WEBM_MAGIC = {0x1A, 0x45, (byte)0xDF, (byte)0xA3};

    @Override
    public void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        StreamingClient client = sessionRegistry.getClientById(session.getId());
        if (client == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }

        StreamingRoom room = roomRegistry.findRoom(client.getRoomId());
        if (room == null) { session.close(CloseStatus.POLICY_VIOLATION); return; }

        if (!client.isCameraEnabled()) return;

        ByteBuffer payload = message.getPayload();
        if (payload.remaining() <= 8) return;

        byte[] raw = new byte[payload.remaining()];
        payload.get(raw);

        // raw = [8-byte timestamp][webm data]
        // Cache if this is a WebM init segment
        if (raw.length > 12) {
            byte[] webm = Arrays.copyOfRange(raw, 8, raw.length);
            if (webm[0] == WEBM_MAGIC[0] && webm[1] == WEBM_MAGIC[1]
            && webm[2] == WEBM_MAGIC[2] && webm[3] == WEBM_MAGIC[3]) {
                initSegmentCache.put(session.getId(), raw);
                log.info("[Handler] Init segment cached for: " + session.getId());
            }
        }

        byte[] senderId = session.getId().getBytes(StandardCharsets.UTF_8);
        ByteBuffer framed = ByteBuffer.allocate(4 + senderId.length + raw.length);
        framed.putInt(senderId.length);
        framed.put(senderId);
        framed.put(raw);
        framed.flip();

        broadcastSink.broadcastBinaryMessage(session.getId(), room.getId(), new BinaryMessage(framed));
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) throws Exception {
        String sessionId = session.getId();
        initSegmentCache.remove(session.getId());
        // ... rest of existing disconnect logic
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
            } else {
                // Get list of session clients and active camera
                List<ClientInfo> clients = room.getAllClients().stream().map(ClientInfo::new).toList();

                String jsonPayload = objectMapper.writeValueAsString(clients);
                broadcastSink.broadcastTextMessage("none", room.getId(), new TextMessage(jsonPayload));
            }
        }
        log.info(String.format("[Handler] Connection closed: %s | Reason code: %d (%s)", 
                sessionId, status.getCode(), status.getReason()));
    }
}
