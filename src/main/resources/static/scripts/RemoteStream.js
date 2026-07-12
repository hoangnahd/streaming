/**
 * RemoteStream
 * ------------
 * One instance per remote participant's video. Owns everything MSE-related
 * for that participant: the MediaSource, the SourceBuffer, the pending
 * packet queue, and its own tiny state machine. Nothing here is global.
 *
 * State machine (replaces ad-hoc checks like `state.sourceBuffer ? ... `):
 *   WAITING_HEADER -> OPENING -> STREAMING -> RESETTING -> STREAMING -> ...
 *                                                        -> DESTROYED
 *
 * Incoming frames are appended through a small internal queue drained on
 * requestAnimationFrame, instead of being pushed into appendBuffer()
 * directly from the WebSocket handler. That keeps bursts of packets from
 * blocking the main thread and smooths out playback.
 */

export const RemoteStreamState = Object.freeze({
  WAITING_HEADER: 'WAITING_HEADER',
  OPENING: 'OPENING',
  STREAMING: 'STREAMING',
  RESETTING: 'RESETTING',
  DESTROYED: 'DESTROYED',
});

export class RemoteStream {
  constructor(id, videoEl) {
    this.id = id;
    this.videoEl = videoEl;

    this.mediaSource = null;
    this.sourceBuffer = null;
    this.queue = [];
    this.pendingInitSegment = null;
    this.state = RemoteStreamState.WAITING_HEADER;

    this._rafHandle = null;

    this._open();
  }

  /** Entry point for every incoming frame (header or media) for this participant. */
  append(frame) {

      const bytes = new Uint8Array(frame);

      const first4 = [...bytes.slice(0,4)]
          .map(b => b.toString(16).padStart(2,"0"))
          .join(" ");

      const isHeader =
          bytes.length >= 4 &&
          bytes[0]===0x1a &&
          bytes[1]===0x45 &&
          bytes[2]===0xdf &&
          bytes[3]===0xa3;

      console.log(
          `[Remote ${this.id}] ${isHeader ? "HEADER" : "MEDIA"} first4=${first4} size=${bytes.length}`
      );

      if (isHeader) {
          this._handleHeader(frame);
          return;
      }

      this._enqueue(frame);
  }

  /**
   * Soft reset: fresh MediaSource, but the <video> element itself is never
   * touched or removed — this is what avoids the flash-to-black you'd get
   * from tearing down and recreating the DOM node on every peer restart.
   */
  reset() {
    this.state = RemoteStreamState.WAITING_HEADER;
    this.sourceBuffer = null;
    this.queue = [];
    this._cancelDrain();
    this._open();
  }

  destroy() {
    this.state = RemoteStreamState.DESTROYED;
    this._cancelDrain();
    this.queue = [];

    if (this.videoEl?.src) {
      URL.revokeObjectURL(this.videoEl.src);
      this.videoEl.removeAttribute('src');
    }

    this.videoEl = null;
    this.mediaSource = null;
    this.sourceBuffer = null;
    this.pendingInitSegment = null;
  }

  // --- internals -----------------------------------------------------

  _open() {
    this.state = RemoteStreamState.OPENING;

    if (this.videoEl.src) {
      URL.revokeObjectURL(this.videoEl.src);
    }

    this.mediaSource = new MediaSource();
    this.videoEl.src = URL.createObjectURL(this.mediaSource);
    this.mediaSource.addEventListener('sourceopen', () => this._onSourceOpen(), { once: true });
  }

  _onSourceOpen() {
      console.log(`[Remote ${this.id}] MediaSource OPEN`);

      if (this.pendingInitSegment) {
          const { data, mimeType } = this.pendingInitSegment;
          this.pendingInitSegment = null;

          this._createSourceBuffer(mimeType);
          this._appendNow(data);
      }
  }

  _createSourceBuffer(mimeType) {
      console.log(`[Remote ${this.id}] Creating SourceBuffer: ${mimeType}`);

      this.sourceBuffer = this.mediaSource.addSourceBuffer(mimeType);

      this.sourceBuffer.addEventListener("updateend", () => {
          console.log(
              `[Remote ${this.id}] updateend buffered=${this.sourceBuffer.buffered.length}`
          );
          this._drainQueue();
      });

      this.sourceBuffer.addEventListener("error", (e) => {
          console.error(`[Remote ${this.id}] SourceBuffer ERROR`, e);
      });

      this.state = RemoteStreamState.STREAMING;
  }

  _handleHeader(frame) {
    const mimeType = detectCodec(frame);
    console.log(
        `[Remote ${this.id}] append HEADER`,
        frame.byteLength
    );

    if (this.sourceBuffer) {
      // Peer's encoder restarted mid-stream (fresh WebM init segment arrived
      // while we already had one). Reset cleanly instead of destroying.
      this.reset();
      this.pendingInitSegment = { data: frame, mimeType };
      return;
    }

    if (this.mediaSource.readyState === 'open') {
      this._createSourceBuffer(mimeType);
      this._appendNow(frame);
    } else {
      this.pendingInitSegment = { data: frame, mimeType };
    }
  }

  _enqueue(frame) {
    this.queue.push(frame);
    this._scheduleDrain();
  }

  _scheduleDrain() {
    if (this._rafHandle !== null) return;
    this._rafHandle = requestAnimationFrame(() => {
      this._rafHandle = null;
      this._drainQueue();
    });
  }

  _cancelDrain() {
    if (this._rafHandle !== null) {
      cancelAnimationFrame(this._rafHandle);
      this._rafHandle = null;
    }
  }

  _drainQueue() {
    if (!this.sourceBuffer || this.sourceBuffer.updating) return;
    if (this.queue.length === 0) return;

    this._appendNow(this.queue.shift());

    if (this.queue.length > 0) this._scheduleDrain();
  }

  _appendNow(frame) {
      if (
          !this.sourceBuffer ||
          this.mediaSource.readyState !== "open" ||
          this.sourceBuffer.updating
      ) {
          this.queue.unshift(frame);
          return;
      }

      try {
          const b = new Uint8Array(frame);

          console.log(
              `[Remote ${this.id}] appendBuffer`,
              `${b[0].toString(16)} ${b[1].toString(16)} ${b[2].toString(16)} ${b[3].toString(16)}`,
              frame.byteLength
          );

          this.sourceBuffer.appendBuffer(frame);

          console.log(`[Remote ${this.id}] appendBuffer OK`);

          if (this.videoEl.paused) {
              this.videoEl.play().catch(() => {});
          }
      } catch (err) {
          console.error(`[Remote ${this.id}] appendBuffer FAILED`, err);
          this.queue.unshift(frame);
      }
  }
}

function isWebMHeader(frame) {
  const b = new Uint8Array(frame, 0, 4);
  return b[0] === 0x1a && b[1] === 0x45 && b[2] === 0xdf && b[3] === 0xa3;
}

function detectCodec(buffer) {
  const bytes = new Uint8Array(buffer);
  for (let i = 0; i < bytes.length - 2; i++) {
    if (bytes[i] === 0x56 && bytes[i + 1] === 0x50 && bytes[i + 2] === 0x39) {
      return 'video/webm; codecs="vp9, opus"';
    }
    if (bytes[i] === 0x56 && bytes[i + 1] === 0x50 && bytes[i + 2] === 0x38) {
      return 'video/webm; codecs="vp8, opus"';
    }
  }
  return 'video/webm; codecs="vp9, opus"';
}