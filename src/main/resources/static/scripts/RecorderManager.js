import { RecorderState, ConnState } from './state.js';

const MIME_CANDIDATES = ['video/webm;codecs=vp8,opus', 'video/webm;codecs=vp8', 'video/webm'];

/**
 * Owns the MediaRecorder and the local send pipeline.
 *
 * Two things this fixes vs. the original:
 *  - `ondataavailable` never awaits anything. It just pushes the Blob into a
 *    queue; a separate drain loop does the async arrayBuffer()+send() work.
 *    That keeps the recorder's own event loop from ever backing up.
 *  - One `state` field (RecorderState) instead of `restartPending` /
 *    `recorderStarting` booleans that could disagree with each other.
 *
 * Emits: 'state-change' { state }, 'chunk-sent' { count }
 */
export class RecorderManager extends EventTarget {
  constructor({ getConnection }) {
    super();
    this.getConnection = getConnection; // () => ConnectionManager
    this.mediaRecorder = null;
    this.localStream = null;
    this.state = RecorderState.STOPPED;
    this.chunkCount = 0;

    this._sendQueue = [];
    this._draining = false;
    this._waitingForInitHeader = true;
    this._pendingInitHeader = new Uint8Array(0);
  }

  setStream(stream) {
    this.localStream = stream;
  }

  start() {
    if (!this.localStream) {
      console.warn('[Recorder] No local stream available.');
      return;
    }
    if (this.state === RecorderState.STARTING || this.state === RecorderState.RECORDING) {
      return; // already running — restart() is the way to force a fresh init segment
    }

    const mimeType = MIME_CANDIDATES.find((t) => MediaRecorder.isTypeSupported(t));
    if (!mimeType) {
      console.error('[Recorder] No supported MIME type.');
      return;
    }

    this._setState(RecorderState.STARTING);
    this._waitingForInitHeader = true;
    this._pendingInitHeader = new Uint8Array(0);

    const recorder = new MediaRecorder(this.localStream, { mimeType, videoBitsPerSecond: 2_500_000 });
    recorder.ondataavailable = (event) => this._onData(event);
    recorder.onstart = () => this._setState(RecorderState.RECORDING);
    recorder.onstop = () => this.dispatchEvent(new CustomEvent('stopped'));

    this.mediaRecorder = recorder;
    recorder.start(100);
  }

  /** Resolves once the recorder has fully stopped (mirrors forceStopRecorder's callback). */
  stop() {
    return new Promise((resolve) => {
      if (!this.mediaRecorder || this.mediaRecorder.state === 'inactive') {
        this._setState(RecorderState.STOPPED);
        resolve();
        return;
      }
      this._setState(RecorderState.STOPPING);
      this.addEventListener('stopped', () => {
        this.mediaRecorder.ondataavailable = null;
        this._setState(RecorderState.STOPPED);
        resolve();
      }, { once: true });
      this.mediaRecorder.stop();
    });
  }

  /** Stop, then start fresh — guarantees a new WebM init segment reaches viewers. */
  async restart() {
    await this.stop();
    this.start();
  }

  _onData(event) {
    if (!event.data || event.data.size === 0) return;
    this._sendQueue.push(event.data);
    this._drainQueue();
  }

  async _drainQueue() {
    if (this._draining) return;
    this._draining = true;
    while (this._sendQueue.length > 0) {
      await this._sendBlob(this._sendQueue.shift());
    }
    this._draining = false;
  }

  async _sendBlob(blob) {
    const connection = this.getConnection();
    if (!connection || connection.state !== ConnState.CONNECTED) return;

    const buf = await blob.arrayBuffer();
    let bytes = new Uint8Array(buf);

    if (this._waitingForInitHeader) {
      bytes = this._mergeInitHeader(bytes);
      if (!bytes) return; // still accumulating a split init header
    }

    const header = new ArrayBuffer(8);
    new DataView(header).setFloat64(0, Date.now());
    const combined = new Uint8Array(8 + bytes.length);
    combined.set(new Uint8Array(header), 0);
    combined.set(bytes, 8);

    connection.send(combined.buffer);
    this.chunkCount++;
    this.dispatchEvent(new CustomEvent('chunk-sent', { detail: { count: this.chunkCount } }));
  }

  // Chrome sometimes splits the very first WebM init segment across two
  // dataavailable events. Buffer until the EBML magic bytes are present.
  _mergeInitHeader(bytes) {
    this._pendingInitHeader = concatUint8(this._pendingInitHeader, bytes);
    const h = this._pendingInitHeader;
    if (h.length < 4) return null;
    if (h[0] !== 0x1a || h[1] !== 0x45 || h[2] !== 0xdf || h[3] !== 0xa3) return null;

    this._waitingForInitHeader = false;
    this._pendingInitHeader = new Uint8Array(0);
    return h;
  }

  _setState(state) {
    this.state = state;
    this.dispatchEvent(new CustomEvent('state-change', { detail: { state } }));
  }
}

function concatUint8(a, b) {
  const out = new Uint8Array(a.length + b.length);
  out.set(a, 0);
  out.set(b, a.length);
  return out;
}