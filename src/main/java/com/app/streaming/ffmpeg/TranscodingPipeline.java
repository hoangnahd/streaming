package com.app.streaming.ffmpeg;

import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;

import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;

public class TranscodingPipeline implements Runnable {
    private static Logger logger = Logger.getLogger(TranscodingPipeline.class.getName());
    private static int MAX_NULL_STREAK = 5;

    FFmpegFrameGrabber grabber;
    FFmpegFrameRecorder recorder;
    CountDownLatch doneLatch;
    // Constructor
    public TranscodingPipeline(FFmpegFrameGrabber grabber, FFmpegFrameRecorder recorder, CountDownLatch doneLatch) {
        this.grabber = grabber;
        this.recorder = recorder;
        this.doneLatch = doneLatch;
    }
    @Override
    public void run() {
        int frameCount = 0;
        int nullStreakCount = 0;

        try {
            logger.fine("[Pipeline] Decode loop started");
            while(nullStreakCount < MAX_NULL_STREAK) {
                Frame frame = grabber.grab();

                if(frame == null) {
                    nullStreakCount++;
                    logger.fine("[Pipeline] Null frame ("+nullStreakCount+"/" + MAX_NULL_STREAK +")");
                    continue;
                }
                nullStreakCount = 0;
                if (frame.image != null || frame.samples != null) {
                    // Preserve grabber timestamps (microseconds); recorder reads these directly.
                    // This prevents A/V drift when the grabber and recorder have different timebases.
                    recorder.setTimestamp(grabber.getTimestamp());
                    recorder.record(frame);
                    frameCount++;

                    if (frameCount % 150 == 0) {
                        logger.info("[Pipeline] " + frameCount + " frames encoded");
                    }
                }

            }
            logger.info("[Pipeline] Stream ended. Frame encoded: "+frameCount);
        } catch (Exception e) {
            logger.severe("[Pipeline] Fatal error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stopQuietly(grabber,  "grabber");
            stopQuietly(recorder, "recorder");
            doneLatch.countDown();
        }
    }
    private void stopQuietly(AutoCloseable resource, String name) {
        try {
            if (resource instanceof FFmpegFrameGrabber g)  { g.stop();  g.release(); }
            if (resource instanceof FFmpegFrameRecorder r) { r.stop();  r.release(); }
        } catch (Exception e) {
            logger.warning("[Pipeline] Error stopping " + name + ": " + e.getMessage());
        }
    }


}
