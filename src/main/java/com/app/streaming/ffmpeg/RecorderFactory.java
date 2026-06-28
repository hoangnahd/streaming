package com.app.streaming.ffmpeg;

import com.app.streaming.gpu.GpuDetector;
import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;

import java.io.OutputStream;

public class RecorderFactory {
    private static final int    WIDTH         = 1280;
    private static final int    HEIGHT        = 720;
    private static final int    VIDEO_BITRATE = 2_500_000;
    private static final int    AUDIO_BITRATE = 128_000;
    private static final int    SAMPLE_RATE   = 48_000;   // Opus requirement
    private static final int    GOP_SECONDS   = 2;        // keyframe interval for MSE players

    public static FFmpegFrameRecorder create(
            OutputStream sink,
            FFmpegFrameGrabber grabber,
            GpuDetector.Backend backend) throws Exception {

        int audioChannels = grabber.getAudioChannels() > 0 ? grabber.getAudioChannels() : 2;
        double frameRate = grabber.getFrameRate() > 0 ? grabber.getFrameRate() : 30.0;
        // Opus only accepts specific sample rates; always normalise to 48000
        int sampleRate = SAMPLE_RATE;

        var recorder = new FFmpegFrameRecorder(sink, WIDTH, HEIGHT, audioChannels);

        // Container
        recorder.setFormat("webm");

        // Common A/V settings
        recorder.setFrameRate(frameRate);
        recorder.setGopSize((int) (frameRate * GOP_SECONDS));  // regular keyframes
        recorder.setAudioCodec(avcodec.AV_CODEC_ID_OPUS);
        recorder.setAudioBitrate(AUDIO_BITRATE);
        recorder.setSampleRate(sampleRate);
        recorder.setVideoBitrate(VIDEO_BITRATE);

        // WebM muxer options — flush clusters promptly to the sink
        recorder.setOption("fflags", "flush_packets");
        recorder.setOption("cluster_time_limit", "500");     // ms
        recorder.setOption("cluster_size_limit", "1048576"); // 1 MB

        applyVideoCodec(recorder, backend);

        recorder.start();
        return recorder;
    }
    private static void applyVideoCodec(
            FFmpegFrameRecorder recorder, GpuDetector.Backend backend) {

        System.out.println("Apply: " + backend);

        switch (backend) {
            case CUDA -> {
                // NVENC has no VP9 encoder; use AV1 NVENC (RTX 30xx+) for WebM.
                // To keep WebM + broad GPU support, swap to h264_nvenc + MP4 if AV1 isn't available.
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_AV1);
                recorder.setVideoOption("codec",       "av1_nvenc");
                recorder.setVideoOption("preset",      "p1");   // fastest NVENC preset
                recorder.setVideoOption("tune",        "ll");   // low-latency
                recorder.setVideoOption("rc",          "cbr");
                recorder.setVideoOption("zerolatency", "1");
            }
            case VAAPI -> {
                // VP9 VAAPI: Intel Gen9+, some AMD GCN5+
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_VP9);
                recorder.setVideoOption("codec",        "vp9_vaapi");
                recorder.setVideoOption("vaapi_device", "/dev/dri/renderD128");
                recorder.setVideoOption("rc_mode",      "CBR");
            }
            default -> {
                // libvpx-vp9 software — tuned for real-time 720p
                recorder.setVideoCodec(avcodec.AV_CODEC_ID_VP9);
                recorder.setOption("deadline",    "realtime");
                recorder.setOption("quality",     "realtime"); // BOTH required for libvpx-vp9
                recorder.setOption("cpu-used",    "6");        // 5–6 sweet spot at 720p
                recorder.setOption("row-mt",      "1");        // row multithreading
                recorder.setOption("tile-columns","2");        // tile parallelism
                recorder.setVideoOption("lag-in-frames",  "0");
                recorder.setVideoOption("error-resilient", "1");
                int threads = Math.min(Runtime.getRuntime().availableProcessors(), 4);
                recorder.setOption("threads", String.valueOf(threads));
            }
        }
    }
}
