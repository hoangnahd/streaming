package com.app.streaming.session;

import org.springframework.web.socket.WebSocketSession;

public class StreamingClient {
    private String profile;
    private String role;
    private final WebSocketSession session;
    private byte[] initHeaderSegment = null;
    private volatile byte[] latestKeyframeCluster = null;

    public StreamingClient(WebSocketSession session, String profile, String role) {
        this.session = session;
        this.profile = profile;
        this.role = role;
    }
    // Getter
    public String getProfile() {
        return this.profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getRole() {
        return this.role;
    }

    public byte[] getInitHeaderSegment() {
        return initHeaderSegment;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public byte[] getLatestKeyframeCluster() {
        return latestKeyframeCluster;
    }

    public String getSessionId() {
        return this.session.getId();
    }

    public WebSocketSession getSession() {
        return this.session;
    }

    // Setter
    public void setInitHeaderSegment(byte[] initHeaderSegment) {
        this.initHeaderSegment = initHeaderSegment;
    }

    public void setLatestKeyframeCluster(byte[] latestKeyframeCluster) {
        this.latestKeyframeCluster = latestKeyframeCluster;
    }

    public void clearCache() {
        this.initHeaderSegment = null;
        this.latestKeyframeCluster = null;
    }

    public boolean isOpen() {
        return session.isOpen();
    }


}
