package com.app.streaming.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.app.streaming.DTO.ClientInfo;
import com.app.streaming.DTO.InitStreamConfig;
import com.app.streaming.model.RoomRegistry;
import com.app.streaming.model.StreamingRoom;


@Controller
public class RoomController {

    @Autowired
    private RoomRegistry roomRegistry;

    @PostMapping("/create/new/room")
    @ResponseBody
    public String createRoom(@RequestBody InitStreamConfig config) {
        String roomId = roomRegistry.createRoom();
        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();

        if(!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        // Using standard query parameters (?video=true&mic=false)
        return baseUrl + "room/" + roomId + "/stream?video="+config.isVideo() + "&mic="+config.isMic();
    }

    @GetMapping("/room/{roomId}/stream")
    public String joinRoom(
        @PathVariable String roomId,
        @RequestParam(name = "video", defaultValue = "false") boolean video,
        @RequestParam(name = "mic", defaultValue = "false") boolean mic,
        Model model
    ) {
        if(!roomRegistry.roomExist(roomId)) {
            return "redirect:/room-not-exist";
        }
        StreamingRoom room = roomRegistry.findRoom(roomId);
        if (room == null) {
            return "redirect:/room-not-exist";
        }

        // 2. Fix: Check if the room is actually full (e.g., maximum 2 or 4 users)
        if (room.isFull()) { 
            return "redirect:/room-full";
        }

        // 3. Pass all configurations directly to your HTML template
        model.addAttribute("roomId", roomId);
        model.addAttribute("video", video);
        model.addAttribute("mic", mic);

        return "room";
    }
}
