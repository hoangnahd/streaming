package com.app.streaming;

import com.app.streaming.handler.AdaptiveVideoBroadcastHandler;
import com.app.streaming.model.BroadcastSink;
import com.app.streaming.model.RoomRegistry;
import com.app.streaming.model.SessionRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StreamingApplicationTests {
	private AdaptiveVideoBroadcastHandler handler;
    private BroadcastSink sink;
	private SessionRegistry sessionSegistry;
	private RoomRegistry roomRegistry;

	@BeforeEach
	void setup() {
		handler = new AdaptiveVideoBroadcastHandler(sessionSegistry, sink, roomRegistry);
	}
	@Test
	void should_RegisterAsViewer_WhenQueryContainsAsViewerParam() throws Exception {
		// Initialize mockSession and attributes
		WebSocketSession mockSession = mock(WebSocketSession.class);

		// Setup for mockSession
		when(mockSession.getId()).thenReturn("viewer-session-123");
		when(mockSession.getUri()).thenReturn(URI.create("wss://localhost:8443/video-stream?isCamActive=true&roomId=room-123"));

		// Act
		handler.afterConnectionEstablished(mockSession);
		// Assert
		assertThat(mockSession.getAttributes().get("ROLE")).isEqualTo("VIEWER");
		assertThat(mockSession.getAttributes().get("PROFILE")).isEqualTo("HIGH");
		verify(mockSession, never()).sendMessage(any(BinaryMessage.class));
	}
}
