package com.app.streaming.pipe;

import java.io.*;
import java.util.concurrent.CountDownLatch;

public class PipeSeeder implements Closeable {
    private static final int PIPE_BUFFER_BYTES = 32*1024*1024; // Max 32mb data exists in pipe
    private static final int SEED_THRESHOLD = 65_536; // 64kb min seed

    private PipedOutputStream writeEnd;
    private PipedInputStream readEnd;
    private final ByteArrayOutputStream seedBuffer = new ByteArrayOutputStream();
    private CountDownLatch seeded = new CountDownLatch(1);
    private volatile boolean seeding = true;
    // Constructor
    public PipeSeeder() throws IOException {
        this.writeEnd = new PipedOutputStream();
        this.readEnd = new PipedInputStream(writeEnd, PIPE_BUFFER_BYTES);
    }
    // Feed the raw chunk into pipe data with 64kb seed threshold
    public void seed(byte[] chunk) throws IOException {
        if(seeding) {
            synchronized (seedBuffer) {
                seedBuffer.write(chunk);
                if(seedBuffer.size() >= SEED_THRESHOLD) {
                    writeEnd.write(seedBuffer.toByteArray());
                    writeEnd.flush();
                    seeding = false;
                    seeded.countDown();
                }
                return;
            }
        } else {
            writeEnd.write(chunk);
        }
    }
    // Wait until the pipe data met the minium of seed data
    public void awaitSeed() throws InterruptedException {
        seeded.await();
    }
    // Get input stream
    public PipedInputStream getInputStream() {
        return this.readEnd;
    }
    // Close the stream
    @Override
    public void close() {
        try {
            writeEnd.close();
        } catch (Exception ignore) {}
    }
}
