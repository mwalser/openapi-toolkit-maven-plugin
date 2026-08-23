import * as esbuild from 'esbuild';
import { mkdirSync, readFileSync } from 'node:fs';

const coreVersion = JSON.parse(readFileSync('node_modules/@redocly/openapi-core/package.json', 'utf8')).version;
const outfile = process.argv[2] || 'dist/redocly-core.mjs';

mkdirSync(outfile.substring(0, outfile.lastIndexOf('/')) || '.', { recursive: true });
const result = await esbuild.build({
  entryPoints: ['src/index.js'],
  bundle: true,
  format: 'esm',
  platform: 'browser',
  mainFields: ['browser', 'module', 'main'],
  target: 'es2022',
  outfile,
  logLevel: 'info',
  metafile: true,
  legalComments: 'none',
  alias: { colorette: './src/shims/colorette.js', 'node:process': './src/shims/process.js', process: './src/shims/process.js', 'node:perf_hooks': './src/shims/perf_hooks.js', perf_hooks: './src/shims/perf_hooks.js', 'node:fs': './src/shims/fs.js', fs: './src/shims/fs.js', 'node:path': 'path-browserify', path: 'path-browserify' },
  define: { 'import.meta.url': '"file:///bundle/redocly-core.mjs"', __REDOCLY_VERSION__: JSON.stringify(coreVersion) },
});
const inputs = Object.entries(result.metafile.inputs).sort((a, b) => b[1].bytes - a[1].bytes).slice(0, 12);
console.log('largest inputs:');
for (const [f, m] of inputs) console.log(`  ${(m.bytes / 1024).toFixed(0).padStart(6)} KB  ${f}`);
