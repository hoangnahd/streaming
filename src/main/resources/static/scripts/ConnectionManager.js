import { ConnState } from './state.js';

/**
 * Owns the WebSocket. Nothing else in the app should touch `new WebSocket(...)`.
 *
 * Emits (via EventTarget, so any manager can `addEventListener` without a
 * hand-rolled pub/sub):
 *   'state-change'   { state }
 *   'open'
 *   'close'          { code, reason }
 *   'json-message'   parsed JSON payload
 *   'remote-packet'  { senderId, packet: ArrayBuffer }  (idLen framing already stripped)
 */
export class ConnectionManager extends EventTarget {
  constructor({ roomId, getConnectParams, maxBackoffMs = 30000 }) {
    super();
    this.roomId = roomId;
    this.getConnectParams = getConnectParams; // () => { video, mic }
    this.maxBackoffMs = maxBackoffMs;
    this.socket = null;
    this.state = ConnState.DISCONNECTED;
    this.reconnectAttempts = 0;
    this._manualClose = false;
  }

  connect() {
    this._manualClose = false;
    this._open();
  }

  disconnect() {
    this._manualClose = true;
    this.socket?.close();
  }

  /** Send a binary ArrayBuffer or JSON-serializable object. Returns false if not connected. */
  send(payload) {
    if (this.state !== ConnState.CONNECTED) return false;
    this.socket.send(payload);
    return true;
  }

  sendJSON(obj) {
    return this.send(JSON.stringify(obj));
  }

  _open() {
    this._setState(this.reconnectAttempts > 0 ? ConnState.RECONNECTING : ConnState.CONNECTING);

    const { video, mic } = this.getConnectParams();
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const url = `${protocol}//${window.location.host}/stream/${this.roomId}?video=${video}&mic=${mic}`;

    const socket = new WebSocket(url);
    socket.binaryType = 'arraybuffer';

    socket.onopen = () => {
      this.reconnectAttempts = 0;
      this._setState(ConnState.CONNECTED);
      this.dispatchEvent(new CustomEvent('open'));
    };

    socket.onmessage = (event) => this._handleMessage(event);

    socket.onclose = (e) => {
      this.dispatchEvent(new CustomEvent('close', { detail: { code: e.code, reason: e.reason } }));
      if (this._manualClose) {
        this._setState(ConnState.DISCONNECTED);
        return;
      }
      this._scheduleReconnect();
    };

    socket.onerror = (err) => {
      this.dispatchEvent(new CustomEvent('error', { detail: err }));
    };

    this.socket = socket;
  }

  _scheduleReconnect() {
    this._setState(ConnState.RECONNECTING);
    const delay = Math.min(1000 * 2 ** this.reconnectAttempts, this.maxBackoffMs);
    this.reconnectAttempts++;
    this.dispatchEvent(new CustomEvent('reconnect-scheduled', { detail: { delay } }));
    setTimeout(() => {
      if (this._manualClose) return;
      this._open();
    }, delay);
  }

  _setState(state) {
    this.state = state;
    this.dispatchEvent(new CustomEvent('state-change', { detail: { state } }));
  }

  _handleMessage(event) {
    if (event.data instanceof ArrayBuffer) {
      this._handleBinary(event.data);
      return;
    }
    try {
      this.dispatchEvent(new CustomEvent('json-message', { detail: JSON.parse(event.data) }));
    } catch (e) {
      console.warn('[Connection] Unhandled non-JSON message:', event.data);
    }
  }

  _handleBinary(buffer) {
    if (buffer.byteLength < 4) return;
    const view = new DataView(buffer);
    const idLen = view.getInt32(0);
    if (idLen <= 0 || idLen > 128 || 4 + idLen > buffer.byteLength) {
      console.error('[Connection] Bad idLen in binary frame:', idLen);
      return;
    }
    const senderId = new TextDecoder().decode(buffer.slice(4, 4 + idLen));
    const packet = buffer.slice(4 + idLen);
    this.dispatchEvent(new CustomEvent('remote-packet', { detail: { senderId, packet } }));
  }
}