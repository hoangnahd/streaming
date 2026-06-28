package com.app.streaming.ffmpeg;

import org.bytedeco.javacv.FFmpegFrameGrabber;

import java.io.PipedInputStream;

public class GrabberFactory {
    public static FFmpegFrameGrabber create(PipedInputStream readInputStream) throws Exception {
        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(readInputStream);
        // Set attribute to grabber
        grabber.setFormat("matroska"); // Handle both .mkv and .webm
        // Probe setting 64kb
        // 100ms analyzeduration prevent audio parameter detect failure
        grabber.setOption("probesize", "65536");
        grabber.setOption("analyzeduration", "100000");
        // Low latency input
//        grabber.setOption("fflags", "nobuffer"); // disable read ahead buffer | error
        grabber.setOption("flags", "low_delay"); // Hint demuxer low delay
        grabber.setOption("avioflags", "direct"); // Bypass AVIOContext buffer
        // Start grabber
        grabber.start();

        return grabber;
    }
    private GrabberFactory() {}
}
