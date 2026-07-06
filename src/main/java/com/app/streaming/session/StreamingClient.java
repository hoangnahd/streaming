package com.app.streaming.session;

import org.springframework.web.socket.WebSocketSession;

public class StreamingClient {
    private String profile;
    private boolean isCamActive;
    private final WebSocketSession session;
    private byte[] initHeaderSegment = null;

    public StreamingClient(WebSocketSession session, String profile, boolean isCamActive) {
        this.session = session;
        this.profile = profile;
        this.isCamActive = isCamActive;
    }
    // Getter
    public String getProfile() {
        return this.profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public boolean isCamActive() {
        return this.isCamActive;
    }

    public byte[] getInitHeaderSegment() {
        return initHeaderSegment;
    }

    public void setIsCamActive(Boolean isCamActive) {
        this.isCamActive = isCamActive;
    }

    public String getSessionId() {
        return this.session.getId();
    }

    public WebSocketSession getSession() {
        return this.session;
    }

    public boolean getIsCamActive() {
        return this.isCamActive;
    }

    // Setter
    public void setInitHeaderSegment(byte[] initHeaderSegment) {
        this.initHeaderSegment = initHeaderSegment;
    }

    public void clearCache() {
        this.initHeaderSegment = null;
    }

    public boolean isOpen() {
        return session.isOpen();
    }
}
