package com.app.streaming.session;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.List;
@Component
public class SessionRegistry {

    private HashMap<String, StreamingClient> clients = new HashMap<>();

    public List<StreamingClient> getClients() {
        return clients.values().stream().toList();
    }

    public StreamingClient getClientById(String sessionId) throws Exception {
        StreamingClient client = clients.get(sessionId);

        if(!client.isOpen()) {
            throw new Exception("[Handler] Session is not alive");
        }

        return clients.get(sessionId);
    }
    // Initialize header
    public boolean isSessionAlive(String sessionId) {
        StreamingClient client = clients.get(sessionId);
        if(client != null && !client.isOpen()) {
            removeSession(client.getSessionId());
            return false;
        }
        return true;
    }
    
    public void registerClient(StreamingClient client) {
        if(clients.get(client.getSessionId()) == null && client.isOpen()) {
            clients.put(client.getSessionId(), client);
            System.out.println("[Session registry]: Session registered successfully");
        } else
            System.out.println("[Session registry]: Session existed or dead!");
    }
    
    public void removeSession(String sessionId) {
        clients.remove(sessionId);
    }

    public void updateInitHeader(String sessionId, byte[] initHeader) {
        StreamingClient client = clients.get(sessionId);
        if (client != null) {
            client.setInitHeaderSegment(initHeader);
        }
    }

    public List<WebSocketSession> getSessions() {
        return this.clients.values().stream().map(StreamingClient::getSession)
                .toList(); // Returns an unmodifiable List<StreamingClient>
    }

    public List<byte[]> getInitHeaders() {
        return this.clients.values().stream().map(StreamingClient::getInitHeaderSegment)
                .toList(); // Returns an unmodifiable List<StreamingClient>
    }
}
