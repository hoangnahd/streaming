package com.app.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.app.streaming.DTO.JoinRoomResponse;
import com.app.streaming.DTO.MessageResponse;
import com.app.streaming.controller.RoomController;
import com.app.streaming.model.StreamingRoom;
import com.app.streaming.registry.RoomRegistry;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    @Mock
    private RoomRegistry roomRegistry;

    @Mock
    private StreamingRoom room;

    @InjectMocks
    private RoomController roomController;


    @Test
    void joinRoom_shouldReturnNotFound_whenRoomIsNull() {

        // Arrange
        String roomId = "room123";

        when(roomRegistry.findRoom(roomId))
                .thenReturn(null);

        // Act
        ResponseEntity<?> response =
                roomController.joinRoom(roomId, true, false);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());

        MessageResponse body =
                assertInstanceOf(MessageResponse.class, response.getBody());

        assertEquals("Room does not exist", body.message());

        // Verify
        verify(roomRegistry).findRoom(roomId);
        verify(room, never()).isFull();
    }


    @Test
    void joinRoom_shouldReturnConflict_whenRoomIsFull() {

        // Arrange
        String roomId = "room123";


        when(roomRegistry.findRoom(roomId))
                .thenReturn(room);

        when(room.isFull())
                .thenReturn(true);

        // Act
        ResponseEntity<?> response =
                roomController.joinRoom(roomId, true, false);

        // Assert
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

        MessageResponse body =
                assertInstanceOf(MessageResponse.class, response.getBody());

        assertEquals("Room is full", body.message());

        // Verify
        verify(roomRegistry).findRoom(roomId);
        verify(room).isFull();
    }


    @Test
    void joinRoom_shouldReturnRoomInfo_whenRoomIsAvailable() {

        // Arrange
        String roomId = "room123";

        when(roomRegistry.findRoom(roomId))
                .thenReturn(room);

        when(room.isFull())
                .thenReturn(false);

        // Act
        ResponseEntity<?> response =
                roomController.joinRoom(roomId, true, false);

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());

        JoinRoomResponse body =
                assertInstanceOf(
                        JoinRoomResponse.class,
                        response.getBody()
                );

        assertEquals("room123", body.roomId());
        assertEquals(true, body.video());
        assertEquals(false, body.mic());

        // Verify
        verify(roomRegistry).findRoom(roomId);
        verify(room).isFull();
    }
}