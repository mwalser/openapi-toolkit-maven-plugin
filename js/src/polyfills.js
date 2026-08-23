// Minimal runtime shims so that @redocly/openapi-core (browser build) runs inside GraalJS.
// The host (JVM) installs a bridge object as `globalThis.__jvm` before this module is evaluated.

const host = globalThis.__jvm;
if (!host) {
  throw new Error('Host bridge `globalThis.__jvm` is not installed');
}

// --- process -------------------------------------------------------------
let cachedCwd = null;
/** The host sets the working directory once per run; `process.cwd()` is called per $ref by openapi-core. */
export function setCwd(dir) { cachedCwd = dir; }
// `platform: 'browser'` makes openapi-core take its browser code paths (console logging, no fs plugins).
if (typeof globalThis.process === 'undefined') {
  globalThis.process = {
    platform: 'browser',
    version: 'v22.0.0',
    versions: { node: '22.0.0' },
    argv: [],
    env: new Proxy({}, { get: (_, key) => (typeof key === 'string' ? host.env(key) ?? undefined : undefined) }),
    cwd: () => cachedCwd ?? (cachedCwd = host.cwd()),
    stdout: { columns: 80, isTTY: false, write: (s) => host.log('output', String(s)) },
    stderr: { columns: 80, isTTY: false, write: (s) => host.log('info', String(s)) },
    nextTick: (fn, ...args) => Promise.resolve().then(() => fn(...args)),
    exit: (code) => { throw new Error(`process.exit(${code}) called`); },
  };
}

// --- console -------------------------------------------------------------
// openapi-core's logger writes through console.* in browser mode. Route everything to the host,
// and support capturing `console.log` output (used by formatProblems) into a buffer.
let captureBuffer = null;
const fmt = (args) => args.map((a) => (typeof a === 'string' ? a : safeStringify(a))).join(' ');
function safeStringify(v) {
  if (v instanceof Error) return v.stack || v.message;
  try { return JSON.stringify(v); } catch { return String(v); }
}
globalThis.console = {
  log: (...a) => { const s = fmt(a); if (captureBuffer !== null) captureBuffer.push(s); else host.log('output', s); },
  info: (...a) => { const s = fmt(a); if (captureBuffer !== null) captureBuffer.push(s); else host.log('info', s); },
  warn: (...a) => host.log('warn', fmt(a)),
  error: (...a) => host.log('error', fmt(a)),
  debug: (...a) => host.log('debug', fmt(a)),
  trace: (...a) => host.log('debug', fmt(a)),
};
export function startCapture() { captureBuffer = []; }
export function stopCapture() { const out = (captureBuffer || []).join(''); captureBuffer = null; return out; }

// --- timers ----------------------------------------------------------------
// GraalJS has no event loop. Timers are queued and drained by the host (see `drainTimers`),
// which the JVM side calls while waiting for a promise to settle.
const timers = new Map();
let timerSeq = 0;
if (typeof globalThis.setTimeout === 'undefined') {
  globalThis.setTimeout = (fn, _ms, ...args) => { const id = ++timerSeq; timers.set(id, () => fn(...args)); return id; };
  globalThis.clearTimeout = (id) => { timers.delete(id); };
  globalThis.setInterval = () => { throw new Error('setInterval is not supported'); };
  globalThis.clearInterval = () => {};
}
if (typeof globalThis.setImmediate === 'undefined') {
  globalThis.setImmediate = (fn, ...args) => globalThis.setTimeout(fn, 0, ...args);
  globalThis.clearImmediate = globalThis.clearTimeout;
}
if (typeof globalThis.queueMicrotask === 'undefined') {
  globalThis.queueMicrotask = (fn) => { Promise.resolve().then(fn); };
}
/** Runs all currently queued timer callbacks. Returns the number of callbacks executed. */
export function drainTimers() {
  const pending = [...timers.entries()];
  timers.clear();
  for (const [, fn] of pending) fn();
  return pending.length;
}

// --- structuredClone -----------------------------------------------------
if (typeof globalThis.structuredClone === 'undefined') {
  globalThis.structuredClone = function clone(v, seen = new Map()) {
    if (v === null || typeof v !== 'object') return v;
    if (seen.has(v)) return seen.get(v);
    if (v instanceof Date) return new Date(v.getTime());
    if (v instanceof RegExp) return new RegExp(v.source, v.flags);
    if (v instanceof Map) { const m = new Map(); seen.set(v, m); for (const [k, x] of v) m.set(clone(k, seen), clone(x, seen)); return m; }
    if (v instanceof Set) { const s = new Set(); seen.set(v, s); for (const x of v) s.add(clone(x, seen)); return s; }
    if (Array.isArray(v)) { const a = []; seen.set(v, a); for (const x of v) a.push(clone(x, seen)); return a; }
    const o = {}; seen.set(v, o);
    for (const k of Object.keys(v)) o[k] = clone(v[k], seen);
    return o;
  };
}

// --- performance -----------------------------------------------------------
if (typeof globalThis.performance === 'undefined') {
  globalThis.performance = { now: () => Number(host.nowMillis()) };
}

// --- URL -----------------------------------------------------------------
// GraalJS does not ship WHATWG URL; delegate parsing to java.net.URI on the host.
if (typeof globalThis.URL === 'undefined') {
  class URL {
    constructor(input, base) {
      const parts = host.parseUrl(String(input), base === undefined ? null : String(base));
      if (parts == null) throw new TypeError(`Invalid URL: ${input}`);
      this.href = parts.href; this.protocol = parts.protocol; this.host = parts.host;
      this.hostname = parts.hostname; this.port = parts.port; this.pathname = parts.pathname;
      this.search = parts.search; this.hash = parts.hash; this.origin = parts.origin;
      this.username = ''; this.password = '';
      this.searchParams = new URLSearchParams(this.search);
    }
    static canParse(input, base) { return host.parseUrl(String(input), base === undefined ? null : String(base)) != null; }
    toString() { return this.href; }
    toJSON() { return this.href; }
  }
  class URLSearchParams {
    constructor(init = '') {
      this._p = [];
      const s = String(init).replace(/^\?/, '');
      if (s) for (const kv of s.split('&')) { const i = kv.indexOf('='); const k = i < 0 ? kv : kv.slice(0, i); const v = i < 0 ? '' : kv.slice(i + 1); this._p.push([dec(k), dec(v)]); }
    }
    get(k) { const e = this._p.find(([x]) => x === k); return e ? e[1] : null; }
    has(k) { return this._p.some(([x]) => x === k); }
    set(k, v) { const i = this._p.findIndex(([x]) => x === k); if (i < 0) this._p.push([k, String(v)]); else { this._p[i][1] = String(v); this._p = this._p.filter(([x], j) => x !== k || j === i); } }
    append(k, v) { this._p.push([k, String(v)]); }
    delete(k) { this._p = this._p.filter(([x]) => x !== k); }
    toString() { return this._p.map(([k, v]) => `${enc(k)}=${enc(v)}`).join('&'); }
    [Symbol.iterator]() { return this._p[Symbol.iterator](); }
  }
  const dec = (s) => { try { return decodeURIComponent(s.replace(/\+/g, ' ')); } catch { return s; } };
  const enc = (s) => encodeURIComponent(s);
  globalThis.URL = URL;
  globalThis.URLSearchParams = URLSearchParams;
}

// --- TextEncoder / TextDecoder (UTF-8 only) --------------------------------
if (typeof globalThis.TextEncoder === 'undefined') {
  globalThis.TextEncoder = class { encode(s) { return new Uint8Array(host.utf8Encode(String(s))); } };
  globalThis.TextDecoder = class { decode(b) { return host.utf8Decode(b); } };
}

// --- fetch -----------------------------------------------------------------
// Remote $refs. openapi-core's readFileFromUrl also accepts config.http.customFetch,
// but a global is simpler and also covers other callers.
if (typeof globalThis.fetch === 'undefined') {
  globalThis.fetch = async (url, init = {}) => {
    const headers = {};
    if (init.headers) for (const k of Object.keys(init.headers)) headers[k] = String(init.headers[k]);
    const res = host.fetch(String(url), init.method || 'GET', JSON.stringify(headers), init.body == null ? null : String(init.body));
    const body = res.body;
    const resHeaders = JSON.parse(res.headersJson);
    const lookup = (n) => { const k = Object.keys(resHeaders).find((h) => h.toLowerCase() === String(n).toLowerCase()); return k ? resHeaders[k] : null; };
    return {
      ok: res.status >= 200 && res.status < 300,
      status: res.status,
      statusText: res.statusText,
      url: String(url),
      headers: { get: lookup, has: (n) => lookup(n) != null },
      text: async () => body,
      json: async () => JSON.parse(body),
    };
  };
}
if (typeof globalThis.AbortController === 'undefined') {
  globalThis.AbortController = class { constructor() { this.signal = { aborted: false, addEventListener() {}, removeEventListener() {} }; } abort() { this.signal.aborted = true; } };
}
