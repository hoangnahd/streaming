// package com.app.streaming;

// import com.app.streaming.handler.AdaptiveVideoBroadcastHandler;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.DisplayName;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;
// import org.springframework.web.socket.BinaryMessage;
// import org.springframework.web.socket.WebSocketSession;

// import java.io.IOException;
// import java.net.URI;
// import java.util.HashMap;
// import java.util.List;
// import java.util.concurrent.CopyOnWriteArrayList;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
// import static org.mockito.Mockito.*;

// class StreamingApplicationTests {
// 	private AdaptiveVideoBroadcastHandler handler;
// 	private List<WebSocketSession> highResViewer;
// 	private List<WebSocketSession> lowResViewer;

// 	@BeforeEach
// 	void setup() {
// 		handler = new AdaptiveVideoBroadcastHandler();
// 		highResViewer = new CopyOnWriteArrayList<WebSocketSession>();
// 		lowResViewer = new CopyOnWriteArrayList<WebSocketSession>();

// 		try {
// 			// Declare field
// 			java.lang.reflect.Field lowResViewerField = handler.getClass().getDeclaredField("lowResViewer");
// 			java.lang.reflect.Field highResViewerField = handler.getClass().getDeclaredField("highResViewer");
// 			// Enable accessible
// 			lowResViewerField.setAccessible(true);
// 			highResViewerField.setAccessible(true);
// 			// Inject field to handler
// 			lowResViewerField.set(handler, lowResViewer);
// 			highResViewerField.set(handler, highResViewer);

// 		} catch (Exception e) {
// 			System.out.println("Error: "+e.getMessage());
// 		}
// 	}
// 	@Test
// 	void should_RegisterAsViewer_WhenQueryContainsAsViewerParam() throws Exception {
// 		// Initialize mockSession and attributes
// 		WebSocketSession mockSession = mock(WebSocketSession.class);
// 		HashMap<String, Object> attributes = new HashMap<>();

// 		// Setup for mockSession
// 		when(mockSession.getId()).thenReturn("viewer-session-123");
// 		when(mockSession.getAttributes()).thenReturn(attributes);
// 		when(mockSession.getUri()).thenReturn(URI.create("wss://localhost:8443/video-stream?role=viewer&profile=high"));

// 		// Act
// 		handler.afterConnectionEstablished(mockSession);
// 		// Assert
// 		assertThat(highResViewer).contains(mockSession);
// 		assertThat(mockSession.getAttributes().get("ROLE")).isEqualTo("VIEWER");
// 		assertThat(mockSession.getAttributes().get("PROFILE")).isEqualTo("HIGH");
// 		verify(mockSession, never()).sendMessage(any(BinaryMessage.class));
// 	}
// 	@Test
// 	public void should_DefaultToCameraAndLowViewer_when_QueryParamIsMissing() throws Exception {
// 		// Initialize mockSession and attributes
// 		WebSocketSession mockSession = mock(WebSocketSession.class);
// 		HashMap<String, Object> attributes = new HashMap<>();

// 		// Setup for mockSession
// 		when(mockSession.getId()).thenReturn("camera-session-123");
// 		when(mockSession.getAttributes()).thenReturn(attributes);
// 		when(mockSession.getUri()).thenReturn(URI.create("wss://localhost:8443/video-stream"));
// 		// Act
// 		handler.afterConnectionEstablished(mockSession);
// 		// Assert
// 		assertThat(lowResViewer).contains(mockSession);
// 		assertThat(mockSession.getAttributes().get("ROLE")).isEqualTo("CAMERA");
// 		assertThat(mockSession.getAttributes().get("PROFILE")).isEqualTo("LOW");
// 	}
// 	@Test
// 	@DisplayName("Should send init header to late viewer when cache is populated")
// 	public void should_sendInitHeader_when_cacheIsPopulated() throws Exception {
// 		// Arrange
// 		byte[] expectedHeader = new byte[]{0x00, 0x1F, 0x43, 0x11};
// 		try {
// 			java.lang.reflect.Field field = AdaptiveVideoBroadcastHandler.class.getDeclaredField("initHeaderSegment");
// 			field.setAccessible(true);
// 			field.set(handler, expectedHeader);
// 		} catch (Exception e) {}
// 		// Initialize mockSession and attributes
// 		WebSocketSession mockViewerSession = mock(WebSocketSession.class);
// 		HashMap<String, Object> attributes = new HashMap<>();

// 		// Setup for mockSession
// 		when(mockViewerSession.getId()).thenReturn("viewer-session-999");
// 		when(mockViewerSession.getAttributes()).thenReturn(attributes);
// 		when(mockViewerSession.getUri()).thenReturn(URI.create("wss://localhost:8443/video-stream?role=viewer"));

// 		handler.afterConnectionEstablished(mockViewerSession);

// 		// Capture the message and pass it to sendMessage to inspect it raw bytes
// 		ArgumentCaptor<BinaryMessage> messageCaptor = ArgumentCaptor.forClass(BinaryMessage.class);
// 		verify(mockViewerSession, times(1)).sendMessage(messageCaptor.capture());
// 		BinaryMessage sentMessage = messageCaptor.getValue();
// 		assertThat(sentMessage.getPayload().array()).containsExactly(expectedHeader);
// 	}
// 	@Test
// 	@DisplayName("Should gracefully handle and catch network errors when push to late viewer fails")
// 	public void should_catchExceptionGracefully_when_ViewerTransmissionCrashes() throws Exception {
// 		// Initialize mockSession and attributes
// 		WebSocketSession breakingSession = mock(WebSocketSession.class);
// 		HashMap<String, Object> attributes = new HashMap<>();

// 		// Setup for mockSession
// 		when(breakingSession.getId()).thenReturn("broken-pipeline-session");
// 		when(breakingSession.getAttributes()).thenReturn(attributes);
// 		when(breakingSession.getUri()).thenReturn(URI.create("wss://localhost:8443/video-stream?role=viewer"));

// 		handler.afterConnectionEstablished(breakingSession);

// 		// Force the socket to simulate an instant network drop when sendMessage is invoked
// 		doThrow(new IOException("Connection reset by peer"))
// 				.when(breakingSession)
// 				.sendMessage(any(BinaryMessage.class));
// 		// Act & Assert
// 		// assertThatCode verifies that the handler catches the exception internally and does not let it crash the thread
// 		assertThatCode(() -> handler.afterConnectionEstablished(breakingSession))
// 				.doesNotThrowAnyException();

// 		// Check that despite the network drop, the session was tracked and stamped with its identity attributes
// 		assertThat(lowResViewer).contains(breakingSession);
// 		assertThat(attributes.get("ROLE")).isEqualTo("VIEWER");
// 	}

// }
