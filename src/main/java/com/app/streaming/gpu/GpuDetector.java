package com.app.streaming.gpu;

import org.bytedeco.ffmpeg.global.avcodec;

/**
 * Detects GPU encoding capability using the JavaCV-native codec registry.
 * No subprocess, no PATH dependency, no IO.
 */
public final class GpuDetector {

    public enum Backend { CUDA, VAAPI, SOFTWARE }

    public static Backend detect() {
        if (isEncoderAvailable("h264_nvenc")) return Backend.CUDA;
        if (isEncoderAvailable("vp9_vaapi"))  return Backend.VAAPI;
        return Backend.SOFTWARE;
    }

    private static boolean isEncoderAvailable(String name) {
        try {
            var codec = avcodec.avcodec_find_encoder_by_name(name);
            return codec != null && !codec.isNull();
        } catch (Exception e) {
            return false;
        }
    }

    private GpuDetector() {}
}