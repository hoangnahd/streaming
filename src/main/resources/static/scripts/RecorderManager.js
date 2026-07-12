import { ConnectionState } from './ConnectionManager.js';

/**
 * RecorderManager
 * ---------------
 * One `state` field instead of the old restartPending / recorderStarting
 * booleans. Started directly from ConnectionManager's 'open' event — there
 * is no polling loop waiting for the socket.
 *
 * ondataavailable never awaits anything: it just pushes the Blob onto a
 * queue and kicks a SenderLoop. The loop does the arrayBuffer() conversion,
 * the split-header merge, and the socket.send() — so a slow encode/send
 * cycle can never block MediaRecorder from firing its next chunk.
 */

export const RecorderState = Object.freeze({
  STOPPED: 'STOPPED',
  STARTING: 'STARTING',
  RECORDING: 'RECORDING',
  STOPPING: 'STOPPING',
  WAITING_RECONNECT: 'WAITING_RECONNECT',
});

const MIME_CANDIDATES = ['video/webm;codecs=vp8,opus', 'video/webm;codecs=vp8', 'video/webm'];

export class RecorderManager extends EventTarget {
  constructor(connectionManager) {
    super();
    this.connection = connectionManager;
    this.mediaRecorder = null;
    this.state = RecorderState.STOPPED;

    this._sendQueue = [];
    this._sending = false;
    this._waitingForInitHeader = true;
    this._pendingInitHeader = new Uint8Array(0);
    this._pendingRestartHeader = new Uint8Array(0);
    
    // Generation counter for discarding stale chunks after restarts
    this._generation = 0; 
  }

  start(stream) {
    this._generation++; // Increment generation on every new start
    
    if (this.state === RecorderState.STARTING || this.state === RecorderState.RECORDING) return;
    if (!stream) return;

    if (this.connection.state !== ConnectionState.CONNECTED) {
      this._setState(RecorderState.WAITING_RECONNECT);
      return;
    }

    const mimeType = MIME_CANDIDATES.find((t) => MediaRecorder.isTypeSupported(t));
    if (!mimeType) {
      console.error('[RecorderManager] No supported MIME type');
      return;
    }

    this._teardownRecorder();
    this._waitingForInitHeader = true;
    this._pendingInitHeader = new Uint8Array(0);

    const recorder = new MediaRecorder(stream, { mimeType, videoBitsPerSecond: 2_500_000 });
    this.mediaRecorder = recorder;
    this._setState(RecorderState.STARTING);

    recorder.ondataavailable = (event) => {
      if (!event.data || event.data.size === 0) return;
      this._sendQueue.push(event.data);
      this._pumpSendLoop();
    };

    recorder.onstart = () => this._setState(RecorderState.RECORDING);
    recorder.onstop = () => this._setState(RecorderState.STOPPED);

    recorder.start(100);
  }

  stop() {
    return new Promise((resolve) => {
      const recorder = this.mediaRecorder;
      if (!recorder || recorder.state === 'inactive') {
        this._setState(RecorderState.STOPPED);
        resolve();
        return;
      }
      this._setState(RecorderState.STOPPING);
      recorder.addEventListener('stop', () => resolve(), { once: true });
      recorder.stop();
    });
  }

  async restart(stream) {
      if (this.state === RecorderState.STOPPING) return;

      await this.stop();

      // Reset everything, importantly _sending to unblock the pump loop
      this._sendQueue.length = 0;
      this._sending = false; 

      this._waitingForInitHeader = true;
      this._pendingInitHeader = new Uint8Array(0);

      this.start(stream);
  }

  // --- internals -----------------------------------------------------

  async _pumpSendLoop() {
    if (this._sending) return;
    this._sending = true;
    
    while (this._sendQueue.length > 0) {
      const blob = this._sendQueue.shift();
      await this._sendChunk(blob);
    }
    
    this._sending = false;
  }

  async _sendChunk(blob) {
    // Capture the generation BEFORE awaiting the arrayBuffer
    const generation = this._generation; 
    
    const buf = await blob.arrayBuffer();

    // If the generation changed during the await, discard this chunk
    if (generation !== this._generation) {
        return; 
    }

    let bytes = new Uint8Array(buf);

    const first4 = [...bytes.slice(0, 4)]
      .map(b => b.toString(16).padStart(2, "0"))
      .join(" ");
      
    console.log(
        "[Recorder raw]",
        bytes.length,
        [...bytes.slice(0, 8)].map(b => b.toString(16).padStart(2, "0")).join(" ")
    );

    const isHeader =
        bytes.length >= 4 &&
        bytes[0] === 0x1a &&
        bytes[1] === 0x45 &&
        bytes[2] === 0xdf &&
        bytes[3] === 0xa3;

    console.log(
        `[Recorder] sending ${isHeader ? "HEADER" : "MEDIA"} (${bytes.length} bytes) first4=${first4}`
    );

    const cluster =
      bytes.length >= 4 &&
      bytes[0] === 0x1f &&
      bytes[1] === 0x43 &&
      bytes[2] === 0xb6 &&
      bytes[3] === 0x75;

    console.log(
        "EBML =", isHeader,
        "Cluster =", cluster,
        "size =", bytes.length
    );

    if (this._waitingForInitHeader) {
        bytes = this._mergeInitHeader(bytes);
        if (bytes === null) return;
    } 

    if (this.connection.state !== ConnectionState.CONNECTED) return; // connection down — drop, don't stall the loop

    const header = new ArrayBuffer(8);
    new DataView(header).setFloat64(0, Date.now());

    const combined = new Uint8Array(8 + bytes.length);
    combined.set(new Uint8Array(header), 0);
    combined.set(bytes, 8);

    this.connection.send(combined.buffer);
    this.dispatchEvent(new CustomEvent('chunk-sent'));
  }

  _mergeInitHeader(bytes) {
    const merged = new Uint8Array(this._pendingInitHeader.length + bytes.length);
    merged.set(this._pendingInitHeader, 0);
    merged.set(bytes, this._pendingInitHeader.length);
    this._pendingInitHeader = merged;

    if (merged.length < 4) return null;

    // Search for the EBML signature anywhere in the merged buffer to prevent deadlocks
    for (let i = 0; i <= merged.length - 4; i++) {
        if (
            merged[i] === 0x1a &&
            merged[i + 1] === 0x45 &&
            merged[i + 2] === 0xdf &&
            merged[i + 3] === 0xa3
        ) {
            const header = merged.slice(i);
            
            this._waitingForInitHeader = false;
            this._pendingInitHeader = new Uint8Array(0);
            
            console.log("[Recorder] First initialization segment detected.");
            return header;
        }
    }

    return null; // Keep waiting, signature not found yet
  }

  _teardownRecorder() {
    const recorder = this.mediaRecorder;
    if (recorder && recorder.state !== 'inactive') recorder.stop();
    if (recorder) recorder.ondataavailable = null;
    this.mediaRecorder = null;
  }

  _setState(state) {
    this.state = state;
    this.dispatchEvent(new CustomEvent('statechange', { detail: state }));
  }
}