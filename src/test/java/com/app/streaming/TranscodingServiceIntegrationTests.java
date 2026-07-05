// package com.app.streaming;

// import com.app.streaming.service.VideoTranscodingService;
// import org.junit.jupiter.api.Test;
// import org.mockito.ArgumentCaptor;
// import org.springframework.web.socket.BinaryMessage;
// import org.springframework.web.socket.WebSocketSession;

// import java.io.InputStream;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;
// import java.util.concurrent.CopyOnWriteArrayList;
// import java.util.concurrent.CountDownLatch;
// import java.util.concurrent.TimeUnit;

// import static org.assertj.core.api.Assertions.assertThat;
// import static org.mockito.Mockito.*;

// public class TranscodingServiceIntegrationTests {

//     @Test
//     public void should_transcode4Kto720p_when_validWebmProvided() throws Exception {
//         // Setup low resolution viewer's session
//         WebSocketSession mockMobileViewer = mock(WebSocketSession.class);
//         when(mockMobileViewer.isOpen()).thenReturn(true);
//         CopyOnWriteArrayList<WebSocketSession> lowResViewer = new CopyOnWriteArrayList<>(Arrays.asList(mockMobileViewer));

//         CountDownLatch ffmpegOutputLatch = new CountDownLatch(1);
//         doAnswer(invocation -> {
//             ffmpegOutputLatch.countDown();
//             return null;
//         }).when(mockMobileViewer).sendMessage(any(BinaryMessage.class));

//         VideoTranscodingService transcodingService = new VideoTranscodingService(lowResViewer);

//         // Resolve relative to project root — works from Maven/Gradle test runner
//         Path videoPath = Path.of("src/test/resources/18800380-uhd_3840_2160_30fps.webm");
//         assertThat(videoPath).exists().isReadable();

//         CountDownLatch transcodeLatch = transcodingService.startTranscodingThread();

//         // No awaitGrabberReady() needed — feedRawHighChunk handles the sync internally
//         System.out.println("[Feed] Starting feed");

//         try (InputStream is = Files.newInputStream(videoPath)) {
//             byte[] buffer = new byte[64 * 1024];
//             int n;
//             while ((n = is.read(buffer)) != -1) {
//                 transcodingService.feedHighRawChunk(Arrays.copyOf(buffer, n));
//             }
//         }

//         System.out.println("[Feed] Feed complete. Closing pipe.");
//         transcodingService.signalEndOfStream();

//         transcodeLatch.await(60, TimeUnit.SECONDS);

//         ArgumentCaptor<BinaryMessage> messageCaptor = ArgumentCaptor.forClass(BinaryMessage.class);
//         verify(mockMobileViewer, atLeastOnce()).sendMessage(messageCaptor.capture());
//         assertThat(messageCaptor.getValue().getPayloadLength()).isGreaterThan(0);
//     }
// }
