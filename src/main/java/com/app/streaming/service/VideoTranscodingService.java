package com.app.streaming.service;

import com.app.streaming.broadcast.BroadcastSink;
import com.app.streaming.ffmpeg.GrabberFactory;
import com.app.streaming.ffmpeg.RecorderFactory;
import com.app.streaming.ffmpeg.TranscodingPipeline;
import com.app.streaming.gpu.GpuDetector;
import com.app.streaming.pipe.PipeSeeder;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;


@Service
public class VideoTranscodingService {
    private static final Logger log = Logger.getLogger(VideoTranscodingService.class.getName());
    private PipeSeeder pipeSeeder;
    private BroadcastSink broadcastSink;

    // Initialize non-parameterized constructor
    public VideoTranscodingService(CopyOnWriteArrayList<WebSocketSession> lowResViewer) throws Exception {
        this.pipeSeeder = new PipeSeeder();
        this.broadcastSink = new BroadcastSink(lowResViewer);
    }
    // Feed raw chunk to data pipeline
    public void feedHighRawChunk(byte[] chunk) throws NullPointerException, IOException {
        pipeSeeder.seed(chunk);
    }
    // Signal end of stream
    public void signalEndOfStream() {
        pipeSeeder.close();
    }

    // Start transcoding thread
    public CountDownLatch startTranscodingThread() {
        GpuDetector.Backend backend = GpuDetector.Backend.SOFTWARE;
        log.info("GPU backend: " + backend);
        CountDownLatch doneLatch = new CountDownLatch(1);

        Thread transcoderThread = new Thread(() -> {

            try {
                // Block until PipeSeeder has written the WebM header seed
                pipeSeeder.awaitSeed();

                FFmpegFrameGrabber  grabber  = GrabberFactory.create(pipeSeeder.getInputStream());
                FFmpegFrameRecorder recorder = RecorderFactory.create(broadcastSink, grabber, backend);
                log.info(String.format("[Service] Grabber ready: ch=%d sr=%d fps=%.2f",
                        grabber.getAudioChannels(), grabber.getSampleRate(), grabber.getFrameRate()));

                new TranscodingPipeline(grabber, recorder, doneLatch).run();
            }
            catch (Exception e) {
                log.severe("[Service] Pipeline init failed: " + e.getMessage());
                doneLatch.countDown();
            }
        }, "FFMPEG-Transcoder");

        transcoderThread.setDaemon(true);
        transcoderThread.start();

        return  doneLatch;
    }
}