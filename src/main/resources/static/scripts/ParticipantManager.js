import { RemoteStream } from './remote-stream.js';

/**
 * Owns the participant roster and their DOM cards. Each participant gets one
 * RemoteStream (see remote-stream.js) created when they join.
 *
 * `_earlyPackets` is the one piece of state that can't live inside
 * RemoteStream, because it covers the window *before* a RemoteStream exists —
 * a binary packet can arrive for a senderId the participant-list JSON hasn't
 * introduced yet. It's still fully scoped to this instance, not global.
 *
 * Emits: 'changed', 'participant-added' { id, el }, 'participant-removed' { id },
 *        'card-clicked' { id }
 */
export class ParticipantManager extends EventTarget {
  constructor({ gridContainer, getLocalSessionId }) {
    super();
    this.gridContainer = gridContainer;
    this.getLocalSessionId = getLocalSessionId;
    this.participants = new Map(); // id -> { id, video, mic, el, remoteStream }
    this._earlyPackets = new Map(); // id -> ArrayBuffer[] (strict arrival order)
  }

  /** clients: array from the server's participant-list broadcast. */
  sync(clients) {
    const serverIds = new Set(clients.map((c) => c.id));

    for (const id of [...this.participants.keys()]) {
      if (!serverIds.has(id)) this._remove(id);
    }

    for (const client of clients) {
      if (client.id === this.getLocalSessionId()) continue;
      if (this.participants.has(client.id)) {
        this._updateIndicators(client);
      } else {
        this._add(client);
      }
    }

    this.dispatchEvent(new CustomEvent('changed'));
  }

  handlePacket(senderId, packet) {
    const participant = this.participants.get(senderId);
    if (!participant) {
      const bucket = this._earlyPackets.get(senderId) ?? [];
      bucket.push(packet);
      this._earlyPackets.set(senderId, bucket);
      return;
    }
    participant.remoteStream.push(packet);
  }

  _add(client) {
    const el = this._createCard(client);
    this.gridContainer.appendChild(el);

    const videoEl = el.querySelector('video');
    const remoteStream = new RemoteStream(client.id, videoEl);
    this.participants.set(client.id, { id: client.id, video: client.video, mic: client.mic, el, remoteStream });

    const early = this._earlyPackets.get(client.id);
    if (early) {
      this._earlyPackets.delete(client.id);
      early.forEach((p) => remoteStream.push(p));
    }

    this.dispatchEvent(new CustomEvent('participant-added', { detail: { id: client.id, el } }));
  }

  _remove(id) {
    const participant = this.participants.get(id);
    if (!participant) return;
    participant.remoteStream.destroy();
    participant.el.remove();
    this.participants.delete(id);
    this._earlyPackets.delete(id);
    this.dispatchEvent(new CustomEvent('participant-removed', { detail: { id } }));
  }

  _updateIndicators(client) {
    const participant = this.participants.get(client.id);
    if (!participant) return;
    participant.video = client.video;
    participant.mic = client.mic;

    const camIcon = participant.el.querySelector(`#ind-cam-${client.id}`);
    const micIcon = participant.el.querySelector(`#ind-mic-${client.id}`);
    const videoEl = participant.el.querySelector('video');
    const avatarEl = participant.el.querySelector('.avatar-box');

    if (camIcon) camIcon.className = `fa-solid ${client.video ? 'fa-video text-emerald-400' : 'fa-video-slash text-red-400'} text-xs`;
    if (micIcon) micIcon.className = `fa-solid ${client.mic ? 'fa-microphone text-emerald-400' : 'fa-microphone-slash text-red-400'} text-xs`;

    if (client.video) {
      videoEl?.classList.remove('hidden');
      avatarEl?.classList.add('hidden');
    } else {
      videoEl?.classList.add('hidden');
      avatarEl?.classList.remove('hidden');
    }
  }

  _createCard(client) {
    const card = document.createElement('div');
    card.setAttribute('data-participant-id', client.id);
    card.className = 'participant-card';
    card.innerHTML = `
      <video id="video-${client.id}" class="${client.video ? '' : 'hidden'} w-full h-full object-cover" autoplay playsinline muted></video>
      <div class="avatar-box ${client.video ? 'hidden' : ''} flex flex-col items-center gap-4">
        <div class="h-20 w-20 rounded-full bg-zinc-800/80 border border-zinc-700/50 flex items-center justify-center text-zinc-500">
          <i class="fa-solid fa-user text-3xl"></i>
        </div>
        <span class="text-xs font-medium text-zinc-600">Connecting...</span>
      </div>
      <div class="absolute bottom-4 left-4 bg-zinc-950/80 backdrop-blur-md border border-zinc-800/60 px-3 py-1.5 rounded-xl text-xs flex items-center gap-2">
        <span class="text-zinc-300">${client.id}</span>
        <i id="ind-cam-${client.id}" class="fa-solid ${client.video ? 'fa-video text-emerald-400' : 'fa-video-slash text-red-400'} text-xs"></i>
        <i id="ind-mic-${client.id}" class="fa-solid ${client.mic ? 'fa-microphone text-emerald-400' : 'fa-microphone-slash text-red-400'} text-xs"></i>
      </div>
    `;
    card.addEventListener('click', () => this.dispatchEvent(new CustomEvent('card-clicked', { detail: { id: client.id } })));
    return card;
  }
}