/**
 * ConnectionManager
 * -----------------
 * Owns the WebSocket, reconnects with exponential backoff (no reconnect
 * storms), and turns raw ws messages into typed events so nothing else in
 * the app touches `event.data` directly.
 *
 * Binary wire format (unchanged from the original):
 *   [ int32 idLen ][ idLen bytes: senderId ][ float64 timestamp ][ payload ]
 *
 * Consumers subscribe with addEventListener:
 *   'statechange' -> ConnectionState
 *   'open'        -> { reconnected: boolean }
 *   'session'     -> localSessionId (string)
 *   'participants'-> full participant array from the server
 *   'force-restart' -> server asked everyone to restart (existing peer joined)
 *   'frame'       -> { senderId, timestamp, payload: ArrayBuffer }
 *   'error'       -> the raw WebSocket error event
 */

export const ConnectionState = Object.freeze({
  DISCONNECTED: 'DISCONNECTED',
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  RECONNECTING: 'RECONNECTING',
});

const BACKOFF_STEPS_MS = [1000, 2000, 4000, 8000, 16000, 30000];

export class ConnectionManager extends EventTarget {
  /**
   * @param {string} roomId
   * @param {() => {video: boolean, mic: boolean}} getParams - read fresh
   *   hardware state on every (re)connect attempt, not just the initial one.
   */
  constructor({ roomId, getParams }) {
    super();
    this.roomId = roomId;
    this.getParams = getParams;

    this.socket = null;
    this.state = ConnectionState.DISCONNECTED;

    this._reconnectAttempt = 0;
    this._reconnectTimer = null;
    this._manualClose = false;
    this._localSessionId = null;
  }

  get localSessionId() {
    return this._localSessionId;
  }

  connect() {
    this._manualClose = false;
    this._openSocket();
  }

  disconnect() {
    this._manualClose = true;
    this._clearReconnectTimer();
    this.socket?.close();
  }

  /** Send a pre-built binary frame (used by RecorderManager). */
  send(buffer) {
    if (this.state !== ConnectionState.CONNECTED) return false;
    this.socket.send(buffer);
    return true;
  }

  /** Send a JSON control message as a text frame. */
  sendJSON(obj) {
    if (this.state !== ConnectionState.CONNECTED) return false;
    this.socket.send(JSON.stringify(obj));
    return true;
  }

  // --- internals -----------------------------------------------------

  _openSocket() {
    this._setState(this._reconnectAttempt > 0 ? ConnectionState.RECONNECTING : ConnectionState.CONNECTING);

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const { video, mic } = this.getParams();
    const url = `${protocol}//${window.location.host}/stream/${this.roomId}?video=${video}&mic=${mic}`;

    const socket = new WebSocket(url);
    socket.binaryType = 'arraybuffer';
    this.socket = socket;

    socket.onopen = () => {
      const wasReconnect = this._reconnectAttempt > 0;
      this._reconnectAttempt = 0;
      this._setState(ConnectionState.CONNECTED);
      this.dispatchEvent(new CustomEvent('open', { detail: { reconnected: wasReconnect } }));
    };

    socket.onmessage = (event) => this._dispatch(event.data);

    socket.onclose = () => {
      this.socket = null;
      if (this._manualClose) {
        this._setState(ConnectionState.DISCONNECTED);
        return;
      }
      this._scheduleReconnect();
    };

    socket.onerror = (err) => {
      this.dispatchEvent(new CustomEvent('error', { detail: err }));
    };
  }

  _scheduleReconnect() {
    this._setState(ConnectionState.RECONNECTING);
    const delay = BACKOFF_STEPS_MS[Math.min(this._reconnectAttempt, BACKOFF_STEPS_MS.length - 1)];
    this._reconnectAttempt++;
    this._clearReconnectTimer();
    this._reconnectTimer = setTimeout(() => this._openSocket(), delay);
  }

  _clearReconnectTimer() {
    if (this._reconnectTimer) {
      clearTimeout(this._reconnectTimer);
      this._reconnectTimer = null;
    }
  }

  _setState(state) {
    this.state = state;
    this.dispatchEvent(new CustomEvent('statechange', { detail: state }));
  }

  _dispatch(data) {
    if (data instanceof ArrayBuffer) {
      this._dispatchBinary(data);
      return;
    }

    try {
      const parsed = JSON.parse(data);

      if (parsed.type === 'SESSION_ID') {
        this._localSessionId = parsed.id;
        this.dispatchEvent(new CustomEvent('session', { detail: parsed.id }));
        return;
      }
      if (parsed.event === 'FORCE_RESTARTED') {
        this.dispatchEvent(new CustomEvent('force-restart'));
        return;
      }
      if (Array.isArray(parsed)) {
        this.dispatchEvent(new CustomEvent('participants', { detail: parsed }));
        return;
      }

      this.dispatchEvent(new CustomEvent('message', { detail: parsed }));
    } catch (e) {
      console.warn('[ConnectionManager] Unhandled message:', data);
    }
  }

  _dispatchBinary(buffer) {
    const view = new DataView(buffer);
    const idLen = view.getInt32(0);

    if (idLen <= 0 || idLen > 128) {
      console.error('[ConnectionManager] Bad idLen in binary frame');
      return;
    }

    const senderId = new TextDecoder().decode(buffer.slice(4, 4 + idLen));
    const envelope = buffer.slice(4 + idLen);
    if (envelope.byteLength < 8) return;

    const timestamp = new DataView(envelope).getFloat64(0);
    const payload = envelope.slice(8);


    const bytes = new Uint8Array(payload);

    const first4 = [...bytes.slice(0, 4)]
        .map(b => b.toString(16).padStart(2, "0"))
        .join(" ");

    const isHeader =
        bytes.length >= 4 &&
        bytes[0] === 0x1a &&
        bytes[1] === 0x45 &&
        bytes[2] === 0xdf &&
        bytes[3] === 0xa3;

    console.log(
        `[Connection] recv ${isHeader ? "HEADER" : "MEDIA"} from ${senderId} first4=${first4}`
    );

    this.dispatchEvent(new CustomEvent('frame', { detail: { senderId, timestamp, payload } }));
  }
}