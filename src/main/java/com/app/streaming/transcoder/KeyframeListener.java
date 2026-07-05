package com.app.streaming.transcoder;

@FunctionalInterface
public interface KeyframeListener {
    void onKeyframeFound(byte[] clusterBytes);
}