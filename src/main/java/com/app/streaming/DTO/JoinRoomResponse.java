package com.app.streaming.DTO;

public record JoinRoomResponse(
    String roomId,
    boolean video,
    boolean mic
) {
} 
