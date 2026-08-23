// Minimal `node:fs` replacement backed by the JVM host bridge: only what openapi-core and the bundled CLI
// commands actually use. Paths are resolved against the working directory of the current run; encoding is
// always UTF-8.
import * as path from 'node:path';
import { callHost, host } from '../host.js';

function hostCall(syscall, p, fn) {
  const target = path.resolve(String(p));
  return callHost(syscall, target, () => fn(target));
}

function notFound(syscall, p) {
  const error = new Error(`ENOENT: no such file or directory, ${syscall} '${p}'`);
  error.code = 'ENOENT';
  error.syscall = syscall;
  error.path = String(p);
  return error;
}

export function existsSync(p) {
  return hostCall('stat', p, (target) => host.exists(target));
}

export function statSync(p) {
  const kind = hostCall('stat', p, (target) => host.statKind(target)); // 'file' | 'dir' | null
  if (kind == null) throw notFound('stat', p);
  return { isDirectory: () => kind === 'dir', isFile: () => kind === 'file', isSymbolicLink: () => false };
}
export const lstatSync = statSync;

export function readFileSync(p, _encoding) {
  const content = hostCall('open', p, (target) => host.readFile(target));
  if (content == null) throw notFound('open', p);
  return content;
}

export function writeFileSync(p, data) {
  hostCall('open', p, (target) => host.writeFile(target, String(data)));
}

export function mkdirSync(p, _options) {
  hostCall('mkdir', p, (target) => host.mkdirs(target));
}

export function readdirSync(p) {
  const entries = hostCall('scandir', p, (target) => host.readdir(target));
  if (entries == null) throw notFound('scandir', p);
  return Array.from(entries);
}

export const promises = {
  readFile: async (p, encoding) => readFileSync(p, encoding),
  writeFile: async (p, data) => writeFileSync(p, data),
  mkdir: async (p, options) => mkdirSync(p, options),
  readdir: async (p) => readdirSync(p),
  stat: async (p) => statSync(p),
  lstat: async (p) => statSync(p),
};

export default { existsSync, statSync, lstatSync, readFileSync, writeFileSync, mkdirSync, readdirSync, promises };
