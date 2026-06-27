package com.app.streaming.service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Receives high-resolution video chunks from a camera stream,
 * transcodes them to 720p using FFmpeg, and broadcasts the
 * lower-resolution stream to viewers with limited bandwidth.
 *
 * Data flow:
 *
 * Camera WebSocket
 *        ↓
 * feedRaw4kChunk()
 *        ↓
 * PipedOutputStream
 *        ↓
 * PipedInputStream
 *        ↓
 * FFmpegFrameGrabber (decode)
 *        ↓
 * FFmpegFrameRecorder (resize + encode)
 *        ↓
 * OutputStream callback
 *        ↓
 * Low-resolution viewers
 */

@Service
public class VideoTranscodingService {
    // Data pipeline
    private static final int FIRST_CHUNK_SIZE = 65_536; // 64KB — matches probesize, covers WebM header
    private PipedOutputStream cameraInputPipe;
    private PipedInputStream readInputStream;
    private List<WebSocketSession> lowResViewers;

    // Accumulates bytes until we have enough for the first chunk
    private final ByteArrayOutputStream firstChunkBuffer = new ByteArrayOutputStream();
    private volatile boolean firstChunkSent = false;

    public VideoTranscodingService(List<WebSocketSession> lowResViewers) throws Exception {
        this.lowResViewers = lowResViewers;
        // Initialize pipeline
        this.cameraInputPipe = new PipedOutputStream();
        this.readInputStream = new PipedInputStream(this.cameraInputPipe, 128 * 1024 * 1024);
    }

    private final CountDownLatch grabberStarted = new CountDownLatch(1);

    // Call this when the camera stream ends
    public void signalEndOfStream() {
        try {
            cameraInputPipe.close();
        } catch (Exception e) {
            System.err.println("Error closing pipe: " + e.getMessage());
        }
    }

    // ── feedRawHighChunk — buffers until first chunk is ready ────────────────────
    public void feedRawHighChunk(byte[] chunk) {
        try {
            if (!firstChunkSent) {
                firstChunkBuffer.write(chunk);

                if (firstChunkBuffer.size() >= FIRST_CHUNK_SIZE) {
                    // Enough header data accumulated — write to pipe and signal grabber
                    cameraInputPipe.write(firstChunkBuffer.toByteArray());
                    cameraInputPipe.flush();
                    firstChunkSent = true;
                    firstChunkBuffer.reset(); // free memory

                    System.out.println("[Feed] First chunk written, signalling grabber...");
                    grabberStarted.countDown(); // grabber.start() can now find data in pipe
                }
            } else {
                // Normal path — pipe directly
                cameraInputPipe.write(chunk);
            }

        } catch (Exception e) {
            System.err.println("Error feeding high chunk: " + e.getMessage());
        }
    }

    public void broadCastLowResViewer(byte[] chunk) {
        BinaryMessage message = new BinaryMessage(ByteBuffer.wrap(chunk));
        try {
            for (WebSocketSession lowResViewer : lowResViewers) {
                lowResViewer.sendMessage(message);
            }
        } catch (Exception e) {
            System.err.println("Error during broadcast to low res viewer: " + e.getMessage());
        }
    }

    public void startTranscodingThread(CountDownLatch latch) {
        Thread transcoderThread = new Thread(() -> {
            FFmpegFrameGrabber grabber = null;
            FFmpegFrameRecorder recorder = null;
            try {
                FFmpegLogCallback.set();

                grabber = new FFmpegFrameGrabber(readInputStream);
                grabber.setFormat("matroska");
                grabber.setOption("probesize", "65536");   // matches FIRST_CHUNK_SIZE
                grabber.setOption("analyzeduration", "0");

                // Wait until producer has written at least the WebM header into the pipe
                System.out.println("[Transcoder] Waiting for first chunk...");
                grabberStarted.await(); // ← blocks here, not before
                System.out.println("[Transcoder] First chunk ready, calling grabber.start()...");

                grabber.start(); // now guaranteed to find data in pipe — no hang
                System.out.println("[Transcoder] grabber.start() returned");

                int audioChannels = grabber.getAudioChannels() > 0 ? grabber.getAudioChannels() : 2;
                int sampleRate = grabber.getSampleRate() > 0 ? grabber.getSampleRate() : 48000;
                double frameRate = grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 30.0;

                System.out.printf("[Grabber] ch=%d sr=%d fps=%.2f%n",
                        audioChannels, sampleRate, frameRate);

                // ── RECORDER ─────────────────────────────────────────────
                OutputStream customOutputStream = new OutputStream() {
                    @Override
                    public void write(int b) {
                        write(new byte[]{(byte) b}, 0, 1);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        byte[] chunk = new byte[len];
                        System.arraycopy(b, off, chunk, 0, len);
                        broadCastLowResViewer(chunk);
                    }
                };

                recorder = new FFmpegFrameRecorder(
                        customOutputStream, 1280, 720, audioChannels
                );
                recorder.setFormat("webm");
                recorder.setOption("f", "webm");
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_VP9);
                recorder.setAudioCodec(avcodec.AV_CODEC_ID_OPUS);
                recorder.setVideoBitrate(2_500_000);
                recorder.setFrameRate(frameRate);
                recorder.setSampleRate(sampleRate);
                recorder.setAudioBitrate(128_000);
                recorder.setOption("deadline", "realtime");
                recorder.setOption("cpu-used", "8");
                recorder.setOption("cluster_time_limit", "500");
                recorder.setOption("cluster_size_limit", "1048576");
                recorder.start();

                // ── DECODE LOOP ───────────────────────────────────────────
                System.out.println("[Transcoder] Starting decode loop");
                Frame frame;
                int frameCount = 0;
                int nullStreak = 0;

                while (nullStreak < 5) {
                    frame = grabber.grab();

                    if (frame == null) {
                        nullStreak++;
                        continue;
                    }
                    nullStreak = 0;

                    if (frame.image != null || frame.samples != null) {
                        recorder.record(frame);
                        frameCount++;
                        if (frameCount % 30 == 0) {
                            System.out.printf("[Transcoder] %d frames%n", frameCount);
                        }
                    }
                }

                System.out.printf("[Transcoder] Done — %d frames%n", frameCount);

            } catch (Exception e) {
                System.err.println("[Transcoder] FATAL: " + e.getMessage());
                e.printStackTrace();
            } finally {
                try {
                    if (recorder != null) recorder.stop();
                } catch (Exception ignored) {
                }
                try {
                    if (grabber != null) grabber.stop();
                } catch (Exception ignored) {
                }
                latch.countDown();
                System.out.println("[Transcoder] Latch released.");
            }
        });

        transcoderThread.setName("FFMPEG-Transcoder-Thread");
        transcoderThread.start();
    }
}