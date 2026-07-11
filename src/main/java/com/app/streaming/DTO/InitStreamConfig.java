package com.app.streaming.DTO;

public class InitStreamConfig {
    private boolean video;
    private boolean mic;

    // 1. FIXED: Added a No-Args constructor (Crucial for Jackson framework instantiation)
    public InitStreamConfig() {}

    // 2. FIXED: Properly map arguments to internal fields
    public InitStreamConfig(boolean video, boolean mic) {
        this.video = video;
        this.mic = mic;
    }

    // Standard Boolean Getters (Conventionally 'is' instead of 'get')
    public boolean isVideo() { return this.video; }
    public boolean isMic() { return this.mic; }

    // Setters
    public void setVideo(boolean video) { this.video = video; }
    public void setMic(boolean mic) { this.mic = mic; }

    @Override
    public String toString() {
        return "StreamConfig{video=" + video + ", mic=" + mic + "}";
    }
}