import { StreamState } from './state.js';

/**
 * One instance per remote participant. Owns its MediaSource, SourceBuffer,
 * and append backpressure queue — nothing about a peer's video lives outside
 * this object except the <video> element itself, which is created by
 * ParticipantManager and handed in.
 *
 * Note on `reset()`: it never touches `videoEl.src` or removes it from the
 * DOM. A peer restarting their recorder mid-call just gets a fresh
 * MediaSource swapped in — that's what avoids the flash-to-black.
 */
export class RemoteStream {
  constructor(id, videoEl) {
    this.id = id;
    this.videoEl = videoEl;
    this.mediaSource = null;
    this.sourceBuffer = null;
    this.appendQueue = [];
    this.pendingInitSegment = null;
    this.state = StreamState.WAITING_HEADER;

    this._openMediaSource();
  }

  /** packet = ArrayBuffer with the 8-byte sender timestamp still attached. */
  push(packet) {
    if (packet.byteLength < 8) return;
    const frame = packet.slice(8);
    if (frame.byteLength < 4) {
      this._appendNow(frame);
      return;
    }

    const head = new Uint8Array(frame, 0, 4);
    const isInitSegment = head[0] === 0x1a && head[1] === 0x45 && head[2] === 0xdf && head[3] === 0xa3;

    if (!isInitSegment) {
      this._appendNow(frame);
      return;
    }

    const mimeType = detectCodec(frame);

    if (this.sourceBuffer) {
      // Peer sent a fresh init segment mid-stream (their recorder restarted).
      this.reset();
      this.pendingInitSegment = { data: frame, mimeType };
      return;
    }

    this.appendQueue = [];
    if (this.mediaSource.readyState === 'open') {
      this._createSourceBuffer(mimeType);
      this._appendNow(frame);
    } else {
      this.pendingInitSegment = { data: frame, mimeType };
    }
  }

  reset() {
    this.state = StreamState.RESETTING;
    this.sourceBuffer = null;
    this.appendQueue = [];
    this._openMediaSource();
  }

  destroy() {
    try { URL.revokeObjectURL(this.videoEl.src); } catch (e) { /* not an object URL, ignore */ }
    this.videoEl.removeAttribute('src');
    this.videoEl.load();
    this.mediaSource = null;
    this.sourceBuffer = null;
    this.appendQueue = [];
    this.pendingInitSegment = null;
  }

  _openMediaSource() {
    this.mediaSource = new MediaSource();
    this.videoEl.src = URL.createObjectURL(this.mediaSource);
    this.state = StreamState.OPENING;

    this.mediaSource.addEventListener('sourceopen', () => {
      if (this.pendingInitSegment) {
        this._createSourceBuffer(this.pendingInitSegment.mimeType);
        this._appendNow(this.pendingInitSegment.data);
        this.pendingInitSegment = null;
      }
    });
  }

  _createSourceBuffer(mimeType) {
    this.sourceBuffer = this.mediaSource.addSourceBuffer(mimeType);
    this.state = StreamState.STREAMING;
    this.sourceBuffer.addEventListener('updateend', () => {
      if (!this.sourceBuffer.updating && this.appendQueue.length > 0) {
        this._appendNow(this.appendQueue.shift());
      }
    });
  }

  _appendNow(frame) {
    const notReady = !this.mediaSource || this.mediaSource.readyState !== 'open'
      || !this.sourceBuffer || this.sourceBuffer.updating || this.appendQueue.length > 0;
    if (notReady) {
      this.appendQueue.push(frame);
      return;
    }
    try {
      this.sourceBuffer.appendBuffer(frame);
      if (this.videoEl.paused) this.videoEl.play().catch(() => {});
    } catch (e) {
      console.error(`[RemoteStream:${this.id}] appendBuffer failed:`, e);
      this.appendQueue.push(frame);
    }
  }
}

function detectCodec(frame) {
  const bytes = new Uint8Array(frame);
  for (let i = 0; i < bytes.length - 2; i++) {
    if (bytes[i] === 0x56 && bytes[i + 1] === 0x50 && bytes[i + 2] === 0x39) return 'video/webm; codecs="vp9, opus"';
    if (bytes[i] === 0x56 && bytes[i + 1] === 0x50 && bytes[i + 2] === 0x38) return 'video/webm; codecs="vp8, opus"';
  }
  return 'video/webm; codecs="vp9, opus"';
}