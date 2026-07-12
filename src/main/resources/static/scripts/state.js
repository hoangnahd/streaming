// Single source of truth for every state machine in the client.
// Rule of thumb: if you're about to add a new boolean flag next to one of
// these, it probably belongs as a new value in the matching enum instead.

export const ConnState = Object.freeze({
  DISCONNECTED: 'DISCONNECTED',
  CONNECTING: 'CONNECTING',
  CONNECTED: 'CONNECTED',
  RECONNECTING: 'RECONNECTING',
});

export const RecorderState = Object.freeze({
  STOPPED: 'STOPPED',
  STARTING: 'STARTING',
  RECORDING: 'RECORDING',
  STOPPING: 'STOPPING',
});

export const StreamState = Object.freeze({
  WAITING_HEADER: 'WAITING_HEADER',
  OPENING: 'OPENING',
  STREAMING: 'STREAMING',
  RESETTING: 'RESETTING',
});