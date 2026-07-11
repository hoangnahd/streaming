package com.app.streaming.model;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class RoomRegistry {
    private ConcurrentHashMap<String, StreamingRoom> rooms = new ConcurrentHashMap<>();

    public String createRoom() {
        String roomId;
        // Generate unique room id
        do {
            roomId = UUID.randomUUID().toString();
        } while(rooms.containsKey(roomId));
        
        rooms.put(roomId, new StreamingRoom(roomId));
        System.out.println("Successfully create a new room: " + roomId);
        return roomId;
    }
    public StreamingRoom findRoom(String roomId) {
        return rooms.get(roomId);
    }
    public boolean roomExist(String roomId) {
        return rooms.get(roomId) != null;
    }
    public void removeRoom(StreamingRoom room) {
        rooms.remove(room);
    }
    public List<StreamingRoom> getAllRooms() {
        return this.rooms.values().stream().toList();
    }
    
}

