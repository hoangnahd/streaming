package com.app.streaming.broadcast;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

public class BroadcastSink extends OutputStream {
    private static final Logger log = Logger.getLogger(BroadcastSink.class.getName());

    private final CopyOnWriteArrayList<WebSocketSession> viewers;
    public BroadcastSink(CopyOnWriteArrayList<WebSocketSession> viewers) {
        this.viewers = viewers;
    }
    public void addViewer(WebSocketSession session) {
        viewers.add(session);
    }

    public void removeViewer(WebSocketSession session) {
        viewers.remove(session);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[]{ (byte) b }, 0, 1);
    }

    @Override
    public void write(byte[] b, int off, int len) {
        byte[] chunk = new byte[len];
        System.arraycopy(b, off, chunk, 0, len);
        var message = new BinaryMessage(chunk);

        for (WebSocketSession viewer : viewers) {
            if (!viewer.isOpen()) {
                viewers.remove(viewer);
                continue;
            }
            try {
                viewer.sendMessage(message);
            } catch (IOException e) {
                log.warning("Dropping viewer " + viewer.getId() + ": " + e.getMessage());
                viewers.remove(viewer);
            }
        }
    }

}
