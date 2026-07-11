package com.app.streaming.model;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class BroadcastSink {

    private final SessionRegistry sessionRegistry;

    public BroadcastSink(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void cacheInitHeader(String cameraSessionId, byte[] initHeader) {
        sessionRegistry.updateInitHeader(cameraSessionId, initHeader);
    }

    public void sendInitHeaders(WebSocketSession session, String roomId) {
        // 1. Guard clause: Check if session is alive first before pulling headers
        if (!sessionRegistry.isSessionAlive(session.getId())) {
            System.out.println("[BroadcastSink] Session is not alive. | Cannot receive headers");
            return;
        }

        List<byte[]> headers = sessionRegistry.getInitHeaders(roomId);
        boolean hasHeader = headers.stream()
                .anyMatch(Objects::nonNull);
        System.out.println("[BroadcastSink]: " + headers);

        if (!hasHeader) {
            return; // Nothing to send
        }
        // 2. Clean, safe loop that handles IOException perfectly
        try {
            for (byte[] header : headers) {
                session.sendMessage(new BinaryMessage(header));
            }
            System.out.println("[BroadcastSink] Successfully sent initialization headers to: " + session.getId());
        } catch (IOException e) {
            System.err.println("[BroadcastSink] Fail sending headers to " + session.getId() + ": " + e.getMessage());
        }
    }

    public void sendBinaryMessage(String skipSessionId, String roomId, BinaryMessage message) {

        List<WebSocketSession> broadCastSession = sessionRegistry.getSessions(roomId).stream()
                                                    .filter(session -> !session.getId().equals(skipSessionId)).toList();

        if (!sessionRegistry.isSessionAlive(skipSessionId)) {
            System.out.println("[BroadcastSink] Sender is not alive.");
            return;
        }
        // Broadcast
        for(WebSocketSession session : broadCastSession) {
            try {
                session.sendMessage(message);
            } catch (Exception e) {
                System.out.println("[BroadcastSink]: Fail streaming to viewer");
            }
        }
    }

    public void sendTextMessage(String skipSessionId, String roomId, TextMessage message) {
        // Filter session based on room id
        List<WebSocketSession> broadCastSession = sessionRegistry.getSessions(roomId);

        // Broadcast
        for(WebSocketSession session : broadCastSession) {
            try {
                if(!session.getId().equals(skipSessionId))
                    session.sendMessage(message);
            } catch (Exception e) {
                System.out.println("[BroadcastSink]: Fail sending text to viewer");
            }
        }
    }
}
