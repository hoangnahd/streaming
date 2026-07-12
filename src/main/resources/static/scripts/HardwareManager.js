/**
 * HardwareManager
 * ---------------
 * Owns getUserMedia, the local MediaStream, and the cam/mic on/off state.
 * Toggle methods flip local state immediately (for the optimistic-UI
 * pattern) and return the new value; setVideoActive/setAudioActive exist
 * so the app can roll back a toggle if the server ever rejects it.
 */

export class HardwareManager extends EventTarget {
  constructor({ videoActive = false, micActive = false } = {}) {
    super();
    this.localStream = null;
    this.videoActive = videoActive;
    this.micActive = micActive;
  }

  async acquire() {
    if (!this.videoActive && !this.micActive) return null;

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720 },
        audio: true,
      });
      stream.getVideoTracks().forEach((t) => (t.enabled = this.videoActive));
      stream.getAudioTracks().forEach((t) => (t.enabled = this.micActive));

      this.localStream = stream;
      this.dispatchEvent(new CustomEvent('stream-ready', { detail: stream }));
      return stream;
    } catch (err) {
      this.videoActive = false;
      this.micActive = false;
      this.dispatchEvent(new CustomEvent('acquire-failed', { detail: err }));
      return null;
    }
  }

  toggleVideo() {
    this.videoActive = !this.videoActive;
    this.localStream?.getVideoTracks().forEach((t) => (t.enabled = this.videoActive));
    this.dispatchEvent(new CustomEvent('video-toggled', { detail: this.videoActive }));
    return this.videoActive;
  }

  toggleAudio() {
    this.micActive = !this.micActive;
    this.localStream?.getAudioTracks().forEach((t) => (t.enabled = this.micActive));
    this.dispatchEvent(new CustomEvent('audio-toggled', { detail: this.micActive }));
    return this.micActive;
  }

  /** Force video state to a specific value — used to roll back a rejected optimistic toggle. */
  setVideoActive(value) {
    this.videoActive = value;
    this.localStream?.getVideoTracks().forEach((t) => (t.enabled = value));
    this.dispatchEvent(new CustomEvent('video-toggled', { detail: value }));
  }

  /** Force mic state to a specific value — used to roll back a rejected optimistic toggle. */
  setAudioActive(value) {
    this.micActive = value;
    this.localStream?.getAudioTracks().forEach((t) => (t.enabled = value));
    this.dispatchEvent(new CustomEvent('audio-toggled', { detail: value }));
  }
}