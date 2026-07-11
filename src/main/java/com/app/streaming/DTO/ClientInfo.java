package com.app.streaming.DTO;

import com.app.streaming.model.StreamingClient;

public class ClientInfo {
    private boolean video;
    private boolean mic;
    private String id;
    // Constructor
    public ClientInfo() {}
    // Mapping constructor
    public ClientInfo(StreamingClient client) {
        this(client.isCameraEnabled(), client.isMicrophoneEnabled(), client.getId());
    }
    public ClientInfo(boolean video, boolean mic, String id) {
        this.video = video;
        this.mic = mic;
        this.id = id;
    }
    // Getter
    public boolean isVideo() {return this.video;}
    public boolean isMic() {return this.mic;}
    public String getId() {return this.id;}
    //Setter
    public void setVideo(boolean video) {this.video = video;}
    public void setMic(boolean mic) {this.mic = mic;}
    public void setId(String id) {this.id = id;}

    @Override
    public String toString() {
        return "StreamConfig{video=" + video + ", mic=" + mic + ", id=" + id + "}";
    }
}
