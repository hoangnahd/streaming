package com.app.streaming.model;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class StreamingRoom {
    String id;
    public StreamingRoom(String id) {
        this.id = id;
    }
    
    ConcurrentHashMap<String, StreamingClient> members = new ConcurrentHashMap<>();
    public boolean isFull() {
        return members.size() >= 2;
    }

    public List<StreamingClient> getAllClients() {
        return this.members.values().stream().toList();
    }

    public boolean addMember(StreamingClient client) {
        if(isFull()) return false;

        System.out.println("New member added");
        // Update member
        members.put(client.getId(), client);
        return true;
    }

    public StreamingClient getMemberById(String id) {
        return members.get(id);
    }

    public boolean isMemberPresent(String memberId) {
        return members.containsKey(memberId);
    }

    public void removeMember(String memberId) {
        members.remove(memberId);
    }

    public int getCurrentSize() {
        return members.size();
    }

    public String getId() {
        return this.id;
    }
}
