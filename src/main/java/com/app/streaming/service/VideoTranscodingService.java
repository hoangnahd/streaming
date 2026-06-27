package com.app.streaming.service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.FFmpegLogCallback;
import org.bytedeco.javacv.Frame;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.*;
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
    private PipedOutputStream cameraInputPipe;
    private PipedInputStream readInputStream;
    private List<WebSocketSession> lowResViewer;
    // Buffer first frame
    private ByteArrayOutputStream firstChunkBuffered = new ByteArrayOutputStream();
    private static final int FIRST_CHUNK_SIZE = 65_536;
    private volatile boolean firstChunkSent = false;
    private CountDownLatch grabberLatch = new CountDownLatch(1);

    // Initialize non-parameterized constructor
    public VideoTranscodingService(List<WebSocketSession> lowResViewer) throws Exception {
        this.cameraInputPipe = new PipedOutputStream();
        this.readInputStream = new PipedInputStream(cameraInputPipe, 32*1024*1024);
        this.lowResViewer = lowResViewer;
    }
    // Feed raw chunk to data pipeline
    public void feedHighRawChunk(byte[] chunk) throws NullPointerException, IOException {

        if(!firstChunkSent) {
            firstChunkBuffered.write(chunk);
            if(firstChunkBuffered.size() >= FIRST_CHUNK_SIZE) {
                // Write first chunk buffered to camera input pipe
                cameraInputPipe.write(firstChunkBuffered.toByteArray());
                cameraInputPipe.flush();
                firstChunkSent = true;

                System.out.println("First chunk written, signalling grabber...");
                firstChunkBuffered.reset(); // Free the mem
                // Count down to unlatch
                grabberLatch.countDown();
            }
        } else {
            cameraInputPipe.write(chunk);
        }
    }
    // Broadcast to viewer
    public void broadcast(byte[] chunk) throws IOException {
        for(WebSocketSession viewer : lowResViewer) {
            viewer.sendMessage(new BinaryMessage(chunk));
        }
    }

    // Signal end of stream
    public void signalEndOfStream() {
        try {
            cameraInputPipe.close();
        } catch (Exception e) {
            System.out.println("Error while closing camera pipe input");
        }
    }
    // Start transcoding thread
    public void startTranscodingThread(CountDownLatch transcodeLatch) {
        Thread transcoderThread = new Thread(() -> {
            // Create grabber
            FFmpegFrameGrabber grabber = null;
            FFmpegFrameRecorder recorder = null;
            try {
                FFmpegLogCallback.set();
                // Grabber
                grabber = new FFmpegFrameGrabber(readInputStream);
                // Set attribute to grabber
                grabber.setFormat("matroska");
                grabber.setOption("probesize", "65536");
                grabber.setOption("analyzeduration", "0");
                // Wait until producer has written at least the WebM header into the pipe
                System.out.println("Waiting for first chunk...");
                grabberLatch.await();
                System.out.println("First chunk ready, calling grabber.start()...");
                grabber.start();

                int audioChannels = grabber.getAudioChannels() > 0 ? grabber.getAudioChannels() : 2;
                int sampleRate = grabber.getSampleRate() > 0 ? grabber.getSampleRate() : 48000;
                double frameRate = grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 30.0;

                System.out.printf("[Grabber] ch=%d sr=%d fps=%.2f%n",
                        audioChannels, sampleRate, frameRate);
                // Recorder
                OutputStream customOutputStream = new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        write(new byte[]{(byte) b}, 0, 1);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        byte[] chunk = new byte[len];
                        System.arraycopy(b, off, chunk, 0, len);
                        broadcast(chunk);
                    }
                };

                recorder = new FFmpegFrameRecorder(customOutputStream, 1280, 720, audioChannels);
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
                        if(frameCount % 30 == 0) {
                            System.out.printf("[Transcoder] %d frames%n", frameCount);
                        }
                    }
                }
                System.out.println("nullStreak: " + nullStreak);
                System.out.println("Transcoder done | FrameCount: " + frameCount);
            }
            catch (Exception e) {
                System.out.println("Error transcoding");
                e.printStackTrace();
            }
            finally {
                // Stop grabber
                try {
                    if(grabber != null) grabber.stop();
                } catch (Exception ignore) {}
                // Stop recorder
                try {
                    if(recorder != null) recorder.stop();
                } catch (Exception ignore) {}
                // Release transcoder latch
                transcodeLatch.countDown();
            }
        });

        transcoderThread.setName("FFMPEG-Transcoder-Thread");
        transcoderThread.start();


    }




}