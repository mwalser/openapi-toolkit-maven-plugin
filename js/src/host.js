// Access to the JVM bridge that the host installs as `globalThis.__jvm` before the bundle is evaluated.

export const host = globalThis.__jvm;
if (!host) {
  throw new Error('Host bridge `globalThis.__jvm` is not installed');
}

/**
 * Invokes a host function and converts its failure into a Node-style error, e.g.
 * `ENOENT: no such file or directory, open '/project/openapi.yaml'` with `error.code === 'ENOENT'`.
 * The host reports failures as `CODE: description` messages (see HostBridge.kt); anything else becomes EIO.
 */
export function callHost(syscall, target, fn) {
  try {
    return fn();
  } catch (failure) {
    const text = failure?.message || String(failure);
    const [, code = 'EIO', description = text] = /^([A-Z][A-Z0-9]*): ([^]*)$/.exec(text) || [];
    const error = new Error(`${code}: ${description}, ${syscall} '${target}'`, { cause: failure });
    error.code = code;
    error.syscall = syscall;
    error.path = target;
    throw error;
  }
}
