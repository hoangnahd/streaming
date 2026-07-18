package com.app.streaming;

import com.app.streaming.handler.AdaptiveVideoBroadcastHandler;
import com.app.streaming.handler.BroadcastSink;
import com.app.streaming.registry.RoomRegistry;
import com.app.streaming.registry.SessionRegistry;
import com.app.streaming.model.StreamingClient;
import com.app.streaming.model.StreamingRoom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;


import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StreamingApplicationTests {
	private AdaptiveVideoBroadcastHandler handler;
    private BroadcastSink sink;
	private SessionRegistry sessionRegistry;
	private RoomRegistry roomRegistry;

	@BeforeEach
	void setup() {
		sessionRegistry = new SessionRegistry();
		roomRegistry = new RoomRegistry();
		sink = new BroadcastSink(sessionRegistry, roomRegistry);
		handler = new AdaptiveVideoBroadcastHandler(sessionRegistry, sink, roomRegistry);
	}
	@Test
	void should_RegisterAsViewer_WhenQueryContainsAsViewerParam() throws Exception {
		// Setup room
		String roomId = roomRegistry.createRoom();
		String link = "wss://localhost:8443/room/" + roomId + "/stream?video=true&mic=true";
		System.out.println("link: " + link);

		// Initialize mockSession and attributes
		WebSocketSession firstClientSession = mock(WebSocketSession.class);
		when(firstClientSession.getId()).thenReturn("first-client");
		when(firstClientSession.getUri()).thenReturn(URI.create(link));

		// Setup for second client session
		WebSocketSession secondClientSession = mock(WebSocketSession.class);
		when(secondClientSession.getId()).thenReturn("second-client");
		when(secondClientSession.getUri()).thenReturn(URI.create(link));

		// Act
		handler.afterConnectionEstablished(firstClientSession);
		handler.afterConnectionEstablished(secondClientSession);

		// Get room
		StreamingRoom room = roomRegistry.findRoom(roomId);
		// Assert
		assertThat(room).isNotNull();
		assertThat(room.getId()).isEqualTo(roomId);
		assertThat(room.getAllClients().stream().map(StreamingClient::getSession))
			.contains(firstClientSession);
		// assertThat(room.getAllClients().stream().map(StreamingClient::getSession))
		// 	.contains(secondClientSession);



	}
}
