import { RemoteStream } from './RemoteStream.js';

/**
 * ParticipantManager
 * ------------------
 * Owns the participant list (a Map, not a Set + a separate remoteStreams
 * object) and the DOM card for each one. Frames that arrive for a
 * participant whose card doesn't exist yet are queued in strict order and
 * flushed the moment the card is created — same guarantee as the original
 * earlyPackets object, just scoped to this module instead of living
 * globally.
 */
export class ParticipantManager extends EventTarget {
  constructor({ gridContainer }) {
    super();
    this.gridContainer = gridContainer;
    this.participants = new Map(); // id -> { info, remoteStream, card }
    this.participants.set('local', { info: { id: 'local' }, remoteStream: null, card: null });

    this._earlyPackets = new Map(); // id -> queued payloads before the card exists
    this._localSessionId = null;
  }

  setLocalSessionId(id) {
    this._localSessionId = id;
  }

  get size() {
    return this.participants.size;
  }

  has(id) {
    return this.participants.has(id);
  }

  /**
   * Dev-only helper backing the "Simulate Join" button — injects a fake
   * participant card with no networking involved. If a real sync() later
   * arrives from the server without this id in it, it gets removed like
   * any other participant that left.
   */
  injectMock(overrides = {}) {
    const mockClient = {
      id: overrides.id ?? `mock-${Math.random().toString(36).slice(2, 8)}`,
      video: overrides.video ?? false,
      mic: overrides.mic ?? false,
    };
    if (this.participants.has(mockClient.id)) return null;
    this._add(mockClient);
    this.dispatchEvent(new CustomEvent('changed'));
    return mockClient.id;
  }

  /** Reconcile against the server's authoritative participant list. */
  sync(clients) {
    const serverIds = new Set(clients.map((c) => c.id));

    for (const id of [...this.participants.keys()]) {
      if (id === 'local') continue;
      if (!serverIds.has(id)) this._remove(id);
    }

    for (const client of clients) {
      if (client.id === this._localSessionId) continue;
      if (!this.participants.has(client.id)) {
        this._add(client);
      } else {
        this._updateIndicators(client);
      }
    }

    this.dispatchEvent(new CustomEvent('changed'));
  }

  /** Route an incoming binary frame to the right RemoteStream (or queue it). */
  routeFrame(senderId, payload) {
    const entry = this.participants.get(senderId);
    if (!entry) {
      const queue = this._earlyPackets.get(senderId) ?? [];
      queue.push(payload);
      this._earlyPackets.set(senderId, queue);
      return;
    }

    if (!entry.remoteStream) {
      const videoEl = entry.card.querySelector('video');
      entry.remoteStream = new RemoteStream(senderId, videoEl);
    }
    entry.remoteStream.append(payload);
  }

  // --- internals -----------------------------------------------------

  _add(client) {
    const card = this._createCard(client);
    this.gridContainer.appendChild(card);

    this.participants.set(client.id, { info: client, remoteStream: null, card });
    this.dispatchEvent(new CustomEvent('joined', { detail: client.id }));

    const queued = this._earlyPackets.get(client.id);
    if (queued) {
      this._earlyPackets.delete(client.id);
      queued.forEach((payload) => this.routeFrame(client.id, payload));
    }
  }

  _remove(id) {
    const entry = this.participants.get(id);
    entry?.remoteStream?.destroy();
    entry?.card?.remove();
    this.participants.delete(id);
    this._earlyPackets.delete(id);
    this.dispatchEvent(new CustomEvent('left', { detail: id }));
  }

  _updateIndicators(client) {
    const entry = this.participants.get(client.id);
    if (!entry) return;
    entry.info = client;

    const camIcon = entry.card.querySelector(`#ind-cam-${client.id}`);
    const micIcon = entry.card.querySelector(`#ind-mic-${client.id}`);
    const videoEl = entry.card.querySelector('video');
    const avatarEl = entry.card.querySelector('.avatar-box');

    if (camIcon) {
      camIcon.className = `fa-solid ${client.video ? 'fa-video text-emerald-400' : 'fa-video-slash text-red-400'} text-xs`;
    }
    if (micIcon) {
      micIcon.className = `fa-solid ${client.mic ? 'fa-microphone text-emerald-400' : 'fa-microphone-slash text-red-400'} text-xs`;
    }

    if (client.video) {
      videoEl?.classList.remove('hidden');
      avatarEl?.classList.add('hidden');
    } else {
      videoEl?.classList.add('hidden');
      avatarEl?.classList.remove('hidden');
      if (entry.remoteStream) {
        entry.remoteStream.destroy();
        entry.remoteStream = null;
      }
    }
  }

  _createCard(client) {
    const card = document.createElement('div');
    card.setAttribute('data-participant-id', client.id);
    card.className =
      'btn-transition relative w-full bg-zinc-900/50 border border-zinc-800/80 rounded-2xl overflow-hidden shadow-xl flex items-center justify-center cursor-pointer group hover:border-zinc-700';
    card.onclick = () => this.dispatchEvent(new CustomEvent('card-click', { detail: client.id }));

    card.innerHTML = `
      <video id="video-${client.id}" class="${client.video ? '' : 'hidden'} w-full h-full object-cover" autoplay playsinline muted></video>
      <div id="avatar-${client.id}" class="avatar-box ${client.video ? 'hidden' : ''} flex flex-col items-center gap-4 transition-all duration-300">
        <div class="h-20 w-20 rounded-full bg-zinc-800/80 border border-zinc-700/50 flex items-center justify-center text-zinc-500 shadow-inner">
          <i class="fa-solid fa-user text-3xl"></i>
        </div>
        <span class="text-xs font-medium tracking-wide text-zinc-600 bg-zinc-950 px-3 py-1 rounded-full border border-zinc-900">Connecting...</span>
      </div>
      <div class="absolute bottom-4 left-4 bg-zinc-950/80 backdrop-blur-md border border-zinc-800/60 px-3 py-1.5 rounded-xl text-xs font-medium flex items-center gap-2 shadow-md">
        <span class="font-medium text-zinc-300">${client.id}</span>
        <div class="flex items-center gap-1 text-zinc-500">
          <i id="ind-cam-${client.id}" class="fa-solid ${client.video ? 'fa-video text-emerald-400' : 'fa-video-slash text-red-400'} text-xs"></i>
          <i id="ind-mic-${client.id}" class="fa-solid ${client.mic ? 'fa-microphone text-emerald-400' : 'fa-microphone-slash text-red-400'} text-xs"></i>
        </div>
      </div>
    `;

    return card;
  }
}