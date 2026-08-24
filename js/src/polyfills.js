// Runtime shims so that @redocly/openapi-core (browser build) runs inside GraalJS. Only what the bundle
// actually reaches is provided; `performance` comes from GraalJS itself (`js.performance` option).
import { callHost, host } from './host.js';

// --- process -----------------------------------------------------------------------------------
let cwd = null;

/** Called by the host once per run; `process.cwd()` is consulted for every `$ref`, so the value is cached. */
export function setCwd(directory) {
  cwd = directory;
}

// `platform: 'browser'` makes openapi-core take its browser code paths: console logging, no plugin loading.
globalThis.process = {
  platform: 'browser',
  version: 'v22.0.0',
  versions: { node: '22.0.0' },
  argv: [],
  env: new Proxy({}, { get: (_, name) => (typeof name === 'string' ? host.env(name) ?? undefined : undefined) }),
  cwd: () => cwd ?? (cwd = host.cwd()),
  stdout: { columns: 80, isTTY: false, write: (text) => host.log('output', String(text)) },
  stderr: { columns: 80, isTTY: false, write: (text) => host.log('info', String(text)) },
  nextTick: (fn, ...args) => Promise.resolve().then(() => fn(...args)),
  exit: (code) => { throw new Error(`process.exit(${code}) called`); },
};

// --- console -----------------------------------------------------------------------------------
// openapi-core's logger writes through console.* in browser mode. Everything goes to the host, except that
// `log` (the logger's `output` channel) and `info` can be captured while a command runs: formatProblems and the
// CLI commands print their results instead of returning them.
let captured = null;

function format(args) {
  return args.map((arg) => (typeof arg === 'string' ? arg : stringify(arg))).join(' ');
}

function stringify(value) {
  if (value instanceof Error) return value.stack || value.message;
  try {
    return JSON.stringify(value);
  } catch {
    return String(value);
  }
}

function emit(channel, args) {
  const text = format(args);
  if (captured) captured.push({ channel, text });
  else host.log(channel, text);
}

globalThis.console = {
  log: (...args) => emit('output', args),
  info: (...args) => emit('info', args),
  warn: (...args) => host.log('warn', format(args)),
  error: (...args) => host.log('error', format(args)),
  debug: (...args) => host.log('debug', format(args)),
  trace: (...args) => host.log('debug', format(args)),
};

/**
 * Runs `fn` and returns its value together with what it printed: `output` is the payload channel alone (what the
 * CLI writes to stdout), `transcript` is everything in order. When `fn` throws, the transcript is attached to the
 * error as `details.output` so that the host can still show it.
 */
export async function captureOutput(fn) {
  const outer = captured;
  const entries = (captured = []);
  const text = (channel) => entries.filter((entry) => !channel || entry.channel === channel).map((entry) => entry.text).join('');
  try {
    const value = await fn();
    return { value, output: text('output'), transcript: text() };
  } catch (error) {
    if (error && typeof error === 'object') error.details = { ...error.details, output: text() };
    throw error;
  } finally {
    captured = outer;
  }
}

// --- timers ------------------------------------------------------------------------------------
// There is no event loop. openapi-core only uses setTimeout to unwind the stack while resolving, so a
// microtask gives the same behavior and lets every command promise settle before control returns to the host
// (see RedoclyRuntime.settledValue). Delays are ignored.
const pendingTimers = new Set();
let nextTimer = 1;

globalThis.setTimeout = (fn, _delay, ...args) => {
  const id = nextTimer++;
  pendingTimers.add(id);
  Promise.resolve().then(() => {
    if (pendingTimers.delete(id)) fn(...args);
  });
  return id;
};
globalThis.clearTimeout = (id) => {
  pendingTimers.delete(id);
};
globalThis.setInterval = () => { throw new Error('setInterval is not supported'); };
globalThis.clearInterval = () => {};

// --- structuredClone ---------------------------------------------------------------------------
globalThis.structuredClone = (value) => {
  const seen = new Map();
  const clone = (v) => {
    if (v === null || typeof v !== 'object') return v;
    if (seen.has(v)) return seen.get(v);
    if (v instanceof Date) return new Date(v.getTime());
    if (v instanceof RegExp) return new RegExp(v.source, v.flags);
    if (v instanceof Map) {
      const copy = new Map();
      seen.set(v, copy);
      for (const [key, item] of v) copy.set(clone(key), clone(item));
      return copy;
    }
    if (v instanceof Set) {
      const copy = new Set();
      seen.set(v, copy);
      for (const item of v) copy.add(clone(item));
      return copy;
    }
    if (Array.isArray(v)) {
      const copy = [];
      seen.set(v, copy);
      for (const item of v) copy.push(clone(item));
      return copy;
    }
    const copy = {};
    seen.set(v, copy);
    for (const key of Object.keys(v)) copy[key] = clone(v[key]);
    return copy;
  };
  return clone(value);
};

// --- URL ---------------------------------------------------------------------------------------
// GraalJS does not ship WHATWG URL; parsing is delegated to java.net.URI on the host.
globalThis.URL = class URL {
  constructor(input, base) {
    const parts = host.parseUrl(String(input), base === undefined ? null : String(base));
    if (parts == null) throw new TypeError(`Invalid URL: ${input}`);
    for (const key of ['href', 'protocol', 'host', 'hostname', 'port', 'pathname', 'search', 'hash', 'origin']) {
      this[key] = parts[key];
    }
    this.username = '';
    this.password = '';
  }

  static canParse(input, base) {
    return host.parseUrl(String(input), base === undefined ? null : String(base)) != null;
  }

  toString() {
    return this.href;
  }

  toJSON() {
    return this.href;
  }
};

// --- fetch -------------------------------------------------------------------------------------
// Remote $refs and `extends` URLs. Only the parts of the Response that openapi-core reads are provided.
globalThis.fetch = async (url, init = {}) => {
  const target = String(url);
  const requestHeaders = {};
  for (const name of Object.keys(init.headers || {})) requestHeaders[name] = String(init.headers[name]);
  const body = init.body == null ? null : String(init.body);
  const response = callHost('fetch', target, () => host.fetch(target, init.method || 'GET', requestHeaders, body));

  const headers = Object.entries(response.headers).map(([name, value]) => [name.toLowerCase(), value]);
  const header = (name) => headers.find(([candidate]) => candidate === String(name).toLowerCase())?.[1] ?? null;
  return {
    ok: response.status >= 200 && response.status < 300,
    status: response.status,
    statusText: '',
    url: target,
    headers: { get: header, has: (name) => header(name) != null },
    text: async () => response.body,
    json: async () => JSON.parse(response.body),
  };
};
