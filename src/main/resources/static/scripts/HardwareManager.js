/**
 * Owns getUserMedia and the local track enable/disable state. Deliberately
 * does NOT touch button/icon DOM — that's a view concern, wired up in main.js
 * off the 'state-change' event, so this class stays testable without a DOM.
 *
 * Emits: 'ready' { stream }, 'state-change', 'cam-toggled' { active }
 */
export class HardwareManager extends EventTarget {
  constructor({ videoEl, initialVideo, initialMic }) {
    super();
    this.videoEl = videoEl;
    this.isCamActive = initialVideo;
    this.isMicActive = initialMic;
    this.localStream = null;
  }

  async init() {
    if (!this.isCamActive && !this.isMicActive) {
      this.dispatchEvent(new CustomEvent('state-change'));
      return;
    }
    try {
      this.localStream = await navigator.mediaDevices.getUserMedia({
        video: { width: 1280, height: 720 },
        audio: true,
      });
      this.localStream.getVideoTracks().forEach((t) => { t.enabled = this.isCamActive; });
      this.localStream.getAudioTracks().forEach((t) => { t.enabled = this.isMicActive; });
      this.videoEl.srcObject = this.localStream;
      this.dispatchEvent(new CustomEvent('ready', { detail: { stream: this.localStream } }));
    } catch (err) {
      console.error('[Hardware] getUserMedia failed:', err);
      this.isCamActive = false;
      this.isMicActive = false;
    }
    this.dispatchEvent(new CustomEvent('state-change'));
  }

  toggleCam() {
    this.isCamActive = !this.isCamActive;
    this.localStream?.getVideoTracks().forEach((t) => { t.enabled = this.isCamActive; });
    this.dispatchEvent(new CustomEvent('state-change'));
    this.dispatchEvent(new CustomEvent('cam-toggled', { detail: { active: this.isCamActive } }));
  }

  toggleMic() {
    this.isMicActive = !this.isMicActive;
    this.localStream?.getAudioTracks().forEach((t) => { t.enabled = this.isMicActive; });
    this.dispatchEvent(new CustomEvent('state-change'));
  }
}