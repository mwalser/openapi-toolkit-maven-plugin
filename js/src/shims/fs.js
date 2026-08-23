// Minimal `node:fs` replacement backed by the JVM host bridge. Only what openapi-core
// and the bundled CLI commands actually use. Paths are plain strings; encoding is always UTF-8.
const host = () => globalThis.__jvm;

function enoent(p) {
  const e = new Error(`ENOENT: no such file or directory, open '${p}'`);
  e.code = 'ENOENT';
  return e;
}

export function existsSync(p) {
  return host().exists(String(p));
}

export function lstatSync(p) {
  const kind = host().statKind(String(p)); // 'file' | 'dir' | null
  if (kind == null) throw enoent(p);
  return { isDirectory: () => kind === 'dir', isFile: () => kind === 'file', isSymbolicLink: () => false };
}
export const statSync = lstatSync;

export function readFileSync(p, _enc) {
  const content = host().readFile(String(p));
  if (content == null) throw enoent(p);
  return content;
}

export function writeFileSync(p, data) {
  host().writeFile(String(p), String(data));
}

export function mkdirSync(p, _opts) {
  host().mkdirs(String(p));
}

export function readdirSync(p) {
  const entries = host().readdir(String(p));
  if (entries == null) throw enoent(p);
  return Array.from({ length: entries.length }, (_, i) => entries[i]);
}

export function unlinkSync(p) {
  host().delete(String(p));
}

export const promises = {
  readFile: async (p, enc) => readFileSync(p, enc),
  writeFile: async (p, data) => writeFileSync(p, data),
  mkdir: async (p, opts) => mkdirSync(p, opts),
  readdir: async (p) => readdirSync(p),
  stat: async (p) => lstatSync(p),
  lstat: async (p) => lstatSync(p),
};

export default { existsSync, lstatSync, statSync, readFileSync, writeFileSync, mkdirSync, readdirSync, unlinkSync, promises };
