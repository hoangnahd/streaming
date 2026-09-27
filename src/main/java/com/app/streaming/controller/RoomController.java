package com.app.streaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.app.streaming.DTO.InitStreamConfig;
import com.app.streaming.DTO.JoinRoomResponse;
import com.app.streaming.DTO.MessageResponse;
import com.app.streaming.registry.RoomRegistry;
import com.app.streaming.model.StreamingRoom;


@RestController 
public class RoomController {

    @Autowired
    private RoomRegistry roomRegistry;

    @GetMapping ("/api/room")
    @ResponseBody
    public String createRoom() {
        String roomId = roomRegistry.createRoom();

        // Using standard query parameters (?video=true&mic=false)
        return roomId;
    }

    @GetMapping("/api/stream")
    public ResponseEntity<?> joinRoom(
        @RequestParam(name = "id", required = true) String roomId,
        @RequestParam(name = "video", defaultValue = "false") boolean video,
        @RequestParam(name = "mic", defaultValue = "false") boolean mic
    ) {
        StreamingRoom room = roomRegistry.findRoom(roomId);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new MessageResponse("Room does not exist"));
        }

        // 2. Fix: Check if the room is actually full (e.g., maximum 2 or 4 users)
        if (room.isFull()) { 
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new MessageResponse("Room is full"));
        }

        return ResponseEntity.ok(
            new JoinRoomResponse(
                roomId, video, mic
            )
        );
    }
}
