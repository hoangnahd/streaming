package com.app.streaming.model;

import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class SessionRegistry {

    private HashMap<String, StreamingClient> clients = new HashMap<>();

    // Initialize header
    public boolean isSessionAlive(String sessionId) {
        StreamingClient client = clients.get(sessionId);
        if(client != null && !client.isOpen()) {
            removeSession(client.getId());
            return false;
        }
        return true;
    }
    
    public void registerClient(StreamingClient client) {
        if(clients.get(client.getId()) == null && client.isOpen()) {
            clients.put(client.getId(), client);
            System.out.println("[Session registry]: Session registered successfully");
        } else
            System.out.println("[Session registry]: Session existed or dead!");
    }
    
    public void removeSession(String sessionId) {
        clients.remove(sessionId);
    }

    public StreamingClient getClientById(String id) {
        return clients.get(id);
    }
}
