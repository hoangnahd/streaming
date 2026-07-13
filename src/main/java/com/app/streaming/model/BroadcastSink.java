package com.app.streaming.model;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
@Component
public class BroadcastSink {

    private final SessionRegistry sessionRegistry;
    private final RoomRegistry roomRegistry;

    public BroadcastSink(SessionRegistry sessionRegistry, RoomRegistry roomRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.roomRegistry = roomRegistry;
    }

    public void broadcastBinaryMessage(String skipSessionId, String roomId, BinaryMessage message) {

        List<WebSocketSession> broadCastSession = roomRegistry.findRoom(roomId).getAllClients().stream()
                                                    .map(StreamingClient::getSession).toList();

        if (!sessionRegistry.isSessionAlive(skipSessionId)) {
            System.out.println("[BroadcastSink] Sender is not alive.");
            return;
        }
        // Broadcast
        for(WebSocketSession session : broadCastSession) {
            try {
                if(!session.getId().equals(skipSessionId))
                    session.sendMessage(message);
            } catch (Exception e) {
                    System.out.println("[BroadcastSink] send failed session=" + session.getId()
                        + " type=" + e.getClass().getSimpleName() + " msg=" + e.getMessage());
                }
        }
    }

    public void broadcastTextMessage(String skipSessionId, String roomId, TextMessage message) {
        // Filter session based on room id
        List<WebSocketSession> broadCastSession = roomRegistry.findRoom(roomId).getAllClients().stream()
                                                                .map(StreamingClient::getSession).toList();

        // Broadcast
        for(WebSocketSession session : broadCastSession) {
            try {
                if(skipSessionId.equals("none") || !session.getId().equals(skipSessionId))
                    session.sendMessage(message);
            } catch (Exception e) {
                System.out.println("[BroadcastSink]: Fail sending text to viewer");
            }
        }
    }

    public void sendBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
