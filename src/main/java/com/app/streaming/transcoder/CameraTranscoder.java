package com.app.streaming.transcoder;

import com.app.streaming.broadcast.BroadcastSink;
import com.app.streaming.model.VideoPacket;
import org.springframework.web.socket.BinaryMessage;

import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Manages a single long-running FFmpeg process per camera session.
 *
 * Data flow:
 *   WebM chunks → stdin → FFmpeg → stdout → BroadcastSink → viewers
 *
 * Three threads:
 *   1. Caller thread  — writes to stdin via feedChunk()
 *   2. Stdout reader  — reads transcoded output, fans out to viewers
 *   3. Stderr reader  — drains FFmpeg logs to prevent deadlock
 */
public class CameraTranscoder implements Closeable {

    private static final Logger log = Logger.getLogger(CameraTranscoder.class.getName());

    // Output chunk size — read stdout in 64 KB blocks
    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private final Process        ffmpegProcess;
    private final OutputStream   ffmpegStdin;
    private final BroadcastSink  broadcastSink;
    private final String         cameraSessionId;
    private final AtomicBoolean  running = new AtomicBoolean(true);
    private final AtomicBoolean  closing = new AtomicBoolean(false);
    private volatile Thread stdoutReader;
    private volatile Thread stderrReader;

    // Holds the timestamp of the most recently written input chunk.
    // Used to re-attach timestamp to transcoded output chunks.
    private volatile long latestInputTimestamp = 0;

    private CameraTranscoder(Process ffmpegProcess,
                             BroadcastSink broadcastSink,
                             String cameraSessionId) {
        this.ffmpegProcess   = ffmpegProcess;
        this.ffmpegStdin     = new BufferedOutputStream(
                ffmpegProcess.getOutputStream(), 256 * 1024); // 256 KB write buffer
        this.broadcastSink   = broadcastSink;
        this.cameraSessionId = cameraSessionId;
    }

    /**
     * Factory method. Starts FFmpeg and the reader threads.
     */
    public static CameraTranscoder start(BroadcastSink broadcastSink,
                                         String cameraSessionId) throws IOException {
        Process process = buildFFmpegProcess();
        CameraTranscoder transcoder = new CameraTranscoder(process, broadcastSink, cameraSessionId);
        transcoder.startReaderThreads();
        log.info("[Transcoder:" + cameraSessionId + "] FFmpeg process started, PID: " + process.pid());
        return transcoder;
    }

    private static Process buildFFmpegProcess() throws IOException {
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-loglevel",   "warning",

                // Input flags — treat stdin as a live stream
                "-fflags",     "nobuffer",
                "-flags",      "low_delay",
                "-fflags",     "nobuffer+discardcorrupt",
                "-analyzeduration", "0",
                "-probesize",  "32768",

                // Input format and source
                "-f",          "webm",
                "-i",          "pipe:0",          // read WebM from stdin

                // Video: scale to 720p, re-encode VP9 realtime
                "-vf",         "scale=1280:720",
                "-c:v",        "libvpx-vp9",
                "-deadline",   "realtime",
                "-cpu-used",   "8",
                "-b:v",        "2500k",
                "-row-mt",     "1",
                "-tile-columns","2",
                "-g",          "60",              // keyframe every 2s at 30fps

                // Audio: pass through Opus without re-encoding
                "-c:a",        "copy",

                // Output format and destination
                "-f",          "webm",
                "pipe:1"                          // write transcoded WebM to stdout
        );

        pb.redirectErrorStream(false); // keep stderr separate
        return pb.start();
    }

    private void startReaderThreads() {
        // Thread 1 — reads transcoded WebM from stdout and broadcasts to viewers
        stdoutReader = new Thread(this::readStdout, "FFmpeg-Stdout-" + cameraSessionId);
        stdoutReader.setDaemon(true);
        stdoutReader.start();

        // Thread 2 — drains stderr to prevent FFmpeg blocking on log output
        stderrReader = new Thread(this::readStderr, "FFmpeg-Stderr-" + cameraSessionId);
        stderrReader.setDaemon(true);
        stderrReader.start();
    }

    private void readStdout() {
        try (InputStream stdout = ffmpegProcess.getInputStream()) {
            byte[] buf = new byte[READ_BUFFER_SIZE];
            int n;
            // No running.get() check here — rely solely on EOF (-1) or IOException.
            // Graceful close(): stdin EOF → FFmpeg finishes encoding + writes trailer
            //                   + exits → its stdout closes → read() returns -1.
            // Forced kill: destroyForcibly() closes the pipe → read() returns -1/throws.
            // Either path drains everything FFmpeg actually wrote before terminating.
            while ((n = stdout.read(buf)) != -1) {
                if (n > 0) {
                    byte[] transcoded = new byte[n];
                    System.arraycopy(buf, 0, transcoded, 0, n);

                    java.nio.ByteBuffer viewerPayload = java.nio.ByteBuffer.allocate(8 + n);
                    viewerPayload.putDouble((double) latestInputTimestamp);
                    viewerPayload.put(transcoded);
                    byte[] viewerBytes = viewerPayload.array();

                    // Update init header
                    if (isWebmInitHeader(transcoded)) {
                        broadcastSink.cacheInitHeader(cameraSessionId, viewerBytes);
                    }
                    // Update latest keyframe
                    if(isKeyframeCluster(transcoded)) {
                        broadcastSink.cacheLatestKeyFrame(cameraSessionId, viewerBytes);
                    }
                    // Broad cast message
                    broadcastSink.sendBinaryMessage(
                        cameraSessionId,
                        new BinaryMessage(viewerBytes)
                    );
                }
            }
        } catch (IOException e) {
            log.warning("[Transcoder:" + cameraSessionId + "] Stdout read error: " + e.getMessage());
        } finally {
            log.info("[Transcoder:" + cameraSessionId + "] Stdout reader exited");
        }
    }

    private boolean isWebmInitHeader(byte[] data) {
        return data.length >= 4 &&
                (data[0] & 0xFF) == 0x1A &&
                (data[1] & 0xFF) == 0x45 &&
                (data[2] & 0xFF) == 0xDF &&
                (data[3] & 0xFF) == 0xA3;
    }

    // WebM keyframe cluster magic bytes: starts with 0x1F43B675
    private boolean isKeyframeCluster(byte[] webmData) {
        if (webmData.length < 4) return false;
        return (webmData[0] & 0xFF) == 0x1F &&
            (webmData[1] & 0xFF) == 0x43 &&
            (webmData[2] & 0xFF) == 0xB6 &&
            (webmData[3] & 0xFF) == 0x75;
    }

    private void readStderr() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(ffmpegProcess.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.warning("[FFmpeg:" + cameraSessionId + "] " + line);
            }
        } catch (IOException e) {
            if (running.get()) {
                log.warning("[Transcoder:" + cameraSessionId + "] Stderr read error: " + e.getMessage());
            }
        }
    }

    /**
     * Feed a parsed VideoPacket into FFmpeg.
     * Only the WebM payload is written to stdin.
     * The timestamp is saved for re-attachment to transcoded output.
     */
    public void feedPacket(VideoPacket packet) throws IOException {
        if (!running.get()) return;
        latestInputTimestamp = packet.getTimestamp();
        ffmpegStdin.write(packet.getWebmData());
        ffmpegStdin.flush();
    }

    /**
     * Graceful shutdown — flush stdin then close it so FFmpeg can drain and exit.
     */
    @Override
    public void close() {
        if (!closing.compareAndSet(false, true)) return;

        log.info("[Transcoder:" + cameraSessionId + "] Shutting down");
        try {
            ffmpegStdin.flush();
            ffmpegStdin.close(); // signals EOF to FFmpeg
        } catch (IOException ignored) {}

        // Give FFmpeg 5 seconds to drain and exit cleanly
        try {
            if (!ffmpegProcess.waitFor(5, TimeUnit.SECONDS)) {
                ffmpegProcess.destroyForcibly();
                log.warning("[Transcoder:" + cameraSessionId + "] FFmpeg force-killed after timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ffmpegProcess.destroyForcibly();
        } finally {
            running.set(false);
        }

        joinReader(stdoutReader, "stdout");
        joinReader(stderrReader, "stderr");

        log.info("[Transcoder:" + cameraSessionId + "] Shutdown complete");
    }

    private void joinReader(Thread reader, String name) {
        if (reader == null) return;

        try {
            reader.join(TimeUnit.SECONDS.toMillis(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warning("[Transcoder:" + cameraSessionId + "] Interrupted while joining " + name + " reader");
        }
    }

    public boolean isRunning() {
        return running.get() && ffmpegProcess.isAlive();
    }
}