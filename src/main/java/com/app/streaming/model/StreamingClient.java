package com.app.streaming.model;

import org.springframework.web.socket.WebSocketSession;

/**
 * Represents a connected client in a streaming session.
 */
public class StreamingClient {

    // --- Fields ---
    private final WebSocketSession session;
    private String roomId;
    private boolean cameraEnabled;
    private boolean microphoneEnabled;
    private byte[] initializationHeaderSegment;

    // --- Constructor ---
    public StreamingClient(WebSocketSession session, String roomId, boolean cameraEnabled, boolean microphoneEnabled) {
        this.session = session;
        this.roomId = roomId;
        this.cameraEnabled = cameraEnabled;
        this.microphoneEnabled = microphoneEnabled;
        this.initializationHeaderSegment = null;
    }

    // --- WebSocket Session Delegation Methods ---
    public WebSocketSession getSession() {
        return this.session;
    }

    public String getSessionId() {
        return this.session.getId();
    }

    public boolean isOpen() {
        return this.session != null && this.session.isOpen();
    }

    // --- Getters and Setters ---

    public String getRoomId() {
        return this.roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public boolean isCameraEnabled() {
        return this.cameraEnabled;
    }

    public void setCameraEnabled(boolean cameraEnabled) {
        this.cameraEnabled = cameraEnabled;
    }

    public boolean isMicrophoneEnabled() {
        return this.microphoneEnabled;
    }

    public void setMicrophoneEnabled(boolean microphoneEnabled) {
        this.microphoneEnabled = microphoneEnabled;
    }

    public byte[] getInitializationHeaderSegment() {
        return this.initializationHeaderSegment;
    }

    public void setInitializationHeaderSegment(byte[] initializationHeaderSegment) {
        this.initializationHeaderSegment = initializationHeaderSegment;
    }

    // --- Business Logic Methods ---
    /**
     * Clears cached initialization stream media headers.
     */
    public void clearHeaderCache() {
        this.initializationHeaderSegment = null;
    }
}