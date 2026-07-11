package com.app.streaming.DTO;

public class StreamConfig {
    private boolean video;
    private boolean mic;
    // Constructor
    public StreamConfig() {}

    public StreamConfig(boolean video, boolean mic) {
        this.video = video;
        this.mic = mic;
    }
    // Getter
    public boolean isVideo() {return this.video;}
    public boolean isMic() {return this.mic;}
    //Setter
    public void setVideo(boolean video) {this.video = video;}
    public void setMic(boolean mic) {this.mic = mic;}

    @Override
    public String toString() {
        return "StreamConfig{video=" + video + ", mic=" + mic + "}";
    }
}
