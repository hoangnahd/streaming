import { ConnectionManager, ConnectionState } from './ConnectionManager.js';
import { RecorderManager } from './RecorderManager.js';
import { HardwareManager } from './HardwareManager.js';
import { ParticipantManager } from './ParticipantManager.js';
import { LayoutManager } from './LayoutManager.js';
console.log("Succefully loaded main js")
/**
 * App
 * ---
 * The only thing that knows about all six managers. Everything else in
 * this file is DOM lookups and event wiring — no business logic lives
 * here beyond "when X happens, tell Y".
 */
class Main {
  constructor(config) {
    this.config = config;
    this.chunkCount = 0;

    this.dom = {
      gridContainer: document.getElementById('participantsGrid'),
      countBadge: document.getElementById('participantCount'),
      netStatus: document.getElementById('netStatus'),
      chunkCountEl: document.getElementById('chunkCount'),
      chunkBadge: document.getElementById('chunkCountBadge'),
      localVideo: document.getElementById('localVideo'),
      btnCam: document.getElementById('btnToggleCam'),
      iconCam: document.getElementById('iconCam'),
      indCam: document.getElementById('indicator-local-cam'),
      avatarLocal: document.getElementById('avatar-local'),
      btnMic: document.getElementById('btnToggleMic'),
      iconMic: document.getElementById('iconMic'),
      indMic: document.getElementById('indicator-local-mic'),
    };

    this.hardware = new HardwareManager({ videoActive: config.video, micActive: config.mic });

    this.connection = new ConnectionManager({
      roomId: config.roomId,
      getParams: () => ({ video: this.hardware.videoActive, mic: this.hardware.micActive }),
    });

    this.recorder = new RecorderManager(this.connection);
    this.participants = new ParticipantManager({ gridContainer: this.dom.gridContainer });
    this.layout = new LayoutManager({ gridContainer: this.dom.gridContainer, countBadge: this.dom.countBadge });

    this._wire();
  }

  async start() {
    this._renderLocalControls();
    await this.hardware.acquire();
    if (this.dom.localVideo) this.dom.localVideo.srcObject = this.hardware.localStream;
    this.layout.rebalance(this.participants.size);
    this.connection.connect();
  }

  async toggleCamera() {
    const next = this.hardware.toggleVideo();
    this._renderLocalControls();
    this._sendConfigUpdate(); // optimistic: UI already reflects `next`, before the server confirms

    if (next) {
      if (this.connection.state === ConnectionState.CONNECTED) {
        this.recorder.start(this.hardware.localStream);
      }
      // If not connected yet, the 'open' handler below starts it once the
      // socket comes up — no polling loop needed.
    } else {
      await this.recorder.stop();
    }
  }

  toggleMic() {
    this.hardware.toggleAudio();
    this._renderLocalControls();
    this._sendConfigUpdate();
  }

  /** Backs the template's "Simulate Join" dev button. No networking involved. */
  simulateJoin() {
    this.participants.injectMock();
  }

  /**
   * Roll back an optimistic toggle. Wire this to a CONFIG_ACK / mismatch
   * message from the server if/when the backend sends one — the original
   * script had no such message, so nothing currently calls this.
   */
  rollbackCamera(value) {
    this.hardware.setVideoActive(value);
    this._renderLocalControls();
  }

  rollbackMic(value) {
    this.hardware.setAudioActive(value);
    this._renderLocalControls();
  }

  // --- wiring ----------------------------------------------------------

  _wire() {
    const statusLabels = {
      [ConnectionState.CONNECTING]: 'Pipeline: Connecting...',
      [ConnectionState.CONNECTED]: 'Pipeline: Connected',
      [ConnectionState.RECONNECTING]: 'Pipeline: Reconnecting...',
      [ConnectionState.DISCONNECTED]: 'Pipeline: Disconnected',
    };
    this.connection.addEventListener('statechange', (e) => {
      this.dom.netStatus.textContent = statusLabels[e.detail] ?? e.detail;
    });

    // socket.onopen -> Recorder.start(), directly. No polling.
    this.connection.addEventListener('open', (e) => {
      if (!this.hardware.localStream || !this.hardware.videoActive) return;

      if (e.detail.reconnected) {
        // Fresh WS session needs a fresh init segment, but existing remote
        // video elements / MediaSources are left completely alone.
        this.recorder.restart(this.hardware.localStream);
      } else {
        this.recorder.start(this.hardware.localStream);
      }
    });

    this.connection.addEventListener('session', (e) => {
      this.participants.setLocalSessionId(e.detail);
    });

    this.connection.addEventListener('participants', (e) => {
      this.participants.sync(e.detail);
      this.layout.rebalance(this.participants.size);
    });

    this.connection.addEventListener('frame', (e) => {
      const { senderId, payload } = e.detail;
      this.participants.routeFrame(senderId, payload);
    });

    this.connection.addEventListener('force-restart', () => {
      if (this.hardware.videoActive && this.hardware.localStream) {
        this.recorder.restart(this.hardware.localStream);
      }
    });

    this.recorder.addEventListener('chunk-sent', () => {
      this.chunkCount++;
      if (this.dom.chunkCountEl) {
        this.dom.chunkCountEl.textContent = this.chunkCount;
        this.dom.chunkBadge?.classList.remove('hidden');
      }
    });

    this.participants.addEventListener('card-click', (e) => this.layout.toggleFocus(e.detail));
    this.participants.addEventListener('changed', () => this.layout.rebalance(this.participants.size));

    // The local card is static HTML (server-rendered), not created by
    // ParticipantManager, so it needs its own click-to-focus wiring here
    // instead of the onclick="handleCardFocus('local')" the old template used.
    document.getElementById('card-local')?.addEventListener('click', () => this.layout.toggleFocus('local'));
  }

  _sendConfigUpdate() {
    this.connection.sendJSON({ type: 'CONFIG_UPDATE', video: this.hardware.videoActive, mic: this.hardware.micActive });
  }

  _renderLocalControls() {
    const { btnCam, iconCam, indCam, localVideo, avatarLocal, btnMic, iconMic, indMic } = this.dom;

    if (this.hardware.videoActive) {
      btnCam.className = 'btn-transition h-12 w-12 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-100 flex items-center justify-center border border-zinc-700/50 cursor-pointer';
      iconCam.className = 'fa-solid fa-video text-base';
      indCam.className = 'fa-solid fa-video text-emerald-400 text-[10px]';
      localVideo.classList.remove('hidden');
      avatarLocal.classList.add('hidden');
    } else {
      btnCam.className = 'btn-transition h-12 w-12 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 flex items-center justify-center border border-red-500/20 cursor-pointer';
      iconCam.className = 'fa-solid fa-video-slash text-base';
      indCam.className = 'fa-solid fa-video-slash text-red-400 text-[10px]';
      localVideo.classList.add('hidden');
      avatarLocal.classList.remove('hidden');
    }

    if (this.hardware.micActive) {
      btnMic.className = 'btn-transition h-12 w-12 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-100 flex items-center justify-center border border-zinc-700/50 cursor-pointer';
      iconMic.className = 'fa-solid fa-microphone text-base';
      indMic.className = 'fa-solid fa-microphone text-emerald-400 text-[10px]';
    } else {
      btnMic.className = 'btn-transition h-12 w-12 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 flex items-center justify-center border border-red-500/20 cursor-pointer';
      iconMic.className = 'fa-solid fa-microphone-slash text-base';
      indMic.className = 'fa-solid fa-microphone-slash text-red-400 text-[10px]';
    }
  }
}

// The only two globals left, and they're functions (not state) needed
// because the existing HTML buttons call onclick="toggleLocalVideoTrack()".
// If you switch those buttons to addEventListener in the template, these
// exports can go away entirely.
const config = window.__ROOM_CONFIG__ ?? { roomId: 'test-room-id', video: false, mic: false };
const app = new Main(config);

window.addEventListener('DOMContentLoaded', () => app.start());
window.toggleLocalVideoTrack = () => app.toggleCamera();
window.toggleLocalAudioTrack = () => app.toggleMic();
window.injectMockRemoteParticipant = () => app.simulateJoin();