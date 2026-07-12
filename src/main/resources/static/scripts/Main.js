import { ConnState } from './state.js';
import { ConnectionManager } from './connection-manager.js';
import { RecorderManager } from './recorder-manager.js';
import { ParticipantManager } from './participant-manager.js';
import { LayoutManager } from './layout-manager.js';
import { HardwareManager } from './hardware-manager.js';

/**
 * Call this once from the Thymeleaf template with the server-injected values.
 * See INTEGRATION.md for the exact <script type="module"> snippet.
 */
export function initStreamingClient({ roomId, video, mic }) {
  let localSessionId = null;

  const gridContainer = document.getElementById('participantsGrid');
  const countBadge = document.getElementById('participantCount');
  const netStatusEl = document.getElementById('netStatus');
  const chunkCountEl = document.getElementById('chunkCount');
  const chunkBadge = document.getElementById('chunkCountBadge');
  const localVideoEl = document.getElementById('localVideo');

  const hardware = new HardwareManager({ videoEl: localVideoEl, initialVideo: video, initialMic: mic });
  const participants = new ParticipantManager({ gridContainer, getLocalSessionId: () => localSessionId });
  const layout = new LayoutManager({ gridContainer, countBadge, participantManager: participants });
  const recorder = new RecorderManager({ getConnection: () => connection });
  const connection = new ConnectionManager({
    roomId,
    getConnectParams: () => ({ video: hardware.isCamActive, mic: hardware.isMicActive }),
  });

  // ---- Connection status → UI ----
  const STATUS_LABELS = {
    [ConnState.CONNECTING]: 'Pipeline: Connecting...',
    [ConnState.CONNECTED]: 'Pipeline: Connected',
    [ConnState.RECONNECTING]: 'Pipeline: Reconnecting...',
    [ConnState.DISCONNECTED]: 'Pipeline: Disconnected',
  };
  connection.addEventListener('state-change', (e) => {
    netStatusEl.textContent = STATUS_LABELS[e.detail.state] ?? e.detail.state;
  });

  // Every (re)connect gets a fresh recorder, which guarantees a fresh WebM
  // init segment reaches viewers — no separate "shouldAutoReconnect" flag needed.
  connection.addEventListener('open', () => {
    if (hardware.localStream && hardware.isCamActive) recorder.restart();
  });

  connection.addEventListener('remote-packet', (e) => {
    participants.handlePacket(e.detail.senderId, e.detail.packet);
  });

  connection.addEventListener('json-message', (e) => {
    const msg = e.detail;
    if (msg.event === 'FORCE_RESTARTED') {
      recorder.restart();
      return;
    }
    if (Array.isArray(msg)) {
      participants.sync(msg);
      return;
    }
    if (msg.type === 'SESSION_ID') {
      localSessionId = msg.id;
    }
  });

  // ---- Recorder chunk counter → UI ----
  recorder.addEventListener('chunk-sent', (e) => {
    chunkCountEl.textContent = e.detail.count;
    chunkBadge.classList.remove('hidden');
  });

  // ---- Hardware → recorder / UI ----
  hardware.addEventListener('state-change', () => applyLocalControlUI(hardware));
  hardware.addEventListener('ready', ({ detail }) => {
    recorder.setStream(detail.stream);
    if (connection.state === ConnState.CONNECTED && hardware.isCamActive) recorder.start();
  });

  function sendConfigUpdate() {
    // Optimistic: UI already reflects the new state before this round-trips.
    // NOTE: the current server protocol has no per-client ack for
    // CONFIG_UPDATE (the participant-list broadcast skips the sender's own
    // entry), so there's no rollback path yet — add a small ack message on
    // the backend before wiring rollback here.
    connection.sendJSON({ type: 'CONFIG_UPDATE', video: hardware.isCamActive, mic: hardware.isMicActive });
  }

  window.toggleLocalVideoTrack = () => {
    hardware.toggleCam();
    sendConfigUpdate();
    if (hardware.isCamActive) {
      recorder.start();
    } else {
      recorder.stop();
    }
  };

  window.toggleLocalAudioTrack = () => {
    hardware.toggleMic();
    sendConfigUpdate();
  };

  window.handleCardFocus = (id) => layout.toggleFocus(id);

  // ---- Boot ----
  hardware.init().then(() => {
    recorder.setStream(hardware.localStream);
    connection.connect();
  });

  return { connection, recorder, participants, layout, hardware };
}

function applyLocalControlUI(hardware) {
  const btnCam = document.getElementById('btnToggleCam');
  const iconCam = document.getElementById('iconCam');
  const indCam = document.getElementById('indicator-local-cam');
  const videoSurface = document.getElementById('localVideo');
  const avatarBox = document.getElementById('avatar-local');

  if (hardware.isCamActive) {
    btnCam.className = 'btn-transition h-12 w-12 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-100 flex items-center justify-center border border-zinc-700/50 cursor-pointer';
    iconCam.className = 'fa-solid fa-video text-base';
    indCam.className = 'fa-solid fa-video text-emerald-400 text-[10px]';
    videoSurface.classList.remove('hidden');
    avatarBox.classList.add('hidden');
  } else {
    btnCam.className = 'btn-transition h-12 w-12 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 flex items-center justify-center border border-red-500/20 cursor-pointer';
    iconCam.className = 'fa-solid fa-video-slash text-base';
    indCam.className = 'fa-solid fa-video-slash text-red-400 text-[10px]';
    videoSurface.classList.add('hidden');
    avatarBox.classList.remove('hidden');
  }

  const btnMic = document.getElementById('btnToggleMic');
  const iconMic = document.getElementById('iconMic');
  const indMic = document.getElementById('indicator-local-mic');

  if (hardware.isMicActive) {
    btnMic.className = 'btn-transition h-12 w-12 rounded-xl bg-zinc-800 hover:bg-zinc-700 text-zinc-100 flex items-center justify-center border border-zinc-700/50 cursor-pointer';
    iconMic.className = 'fa-solid fa-microphone text-base';
    indMic.className = 'fa-solid fa-microphone text-emerald-400 text-[10px]';
  } else {
    btnMic.className = 'btn-transition h-12 w-12 rounded-xl bg-red-600/20 hover:bg-red-600/30 text-red-400 flex items-center justify-center border border-red-500/20 cursor-pointer';
    iconMic.className = 'fa-solid fa-microphone-slash text-base';
    indMic.className = 'fa-solid fa-microphone-slash text-red-400 text-[10px]';
  }
}