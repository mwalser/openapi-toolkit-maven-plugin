// `node:process` replacement: re-exports the polyfilled global.
const p = globalThis.process;
export const cwd = () => p.cwd();
export const env = p.env;
export const platform = p.platform;
export const argv = p.argv;
export const version = p.version;
export const stdout = p.stdout;
export const stderr = p.stderr;
export default p;
