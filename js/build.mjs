// Bundles src/index.js together with @redocly/openapi-core and the vendored CLI commands into one ES module
// that GraalJS can evaluate, plus the license notices of everything the bundle contains.
//
//   node build.mjs [bundle file] [notices file]     (defaults: dist/redocly-core.mjs, dist/THIRD-PARTY-NOTICES.txt)
import * as esbuild from 'esbuild';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import * as path from 'node:path';

const coreVersion = JSON.parse(readFileSync('node_modules/@redocly/openapi-core/package.json', 'utf8')).version;
const pomVersion = readFileSync('../pom.xml', 'utf8').match(/<redocly\.version>([^<]+)<\/redocly\.version>/)?.[1];
if (pomVersion !== coreVersion) {
  throw new Error(`Redocly version mismatch: package.json has ${coreVersion}, pom.xml has ${pomVersion}`);
}

const outfile = process.argv[2] || 'dist/redocly-core.mjs';
const noticesFile = process.argv[3] || 'dist/THIRD-PARTY-NOTICES.txt';
mkdirSync(path.dirname(outfile), { recursive: true });
mkdirSync(path.dirname(noticesFile), { recursive: true });

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
  legalComments: 'none', // every license is reproduced in THIRD-PARTY-NOTICES.txt instead
  plugins: [browserEnvWithHostVariables()],
  alias: {
    'node:fs': './src/shims/fs.js',
    fs: './src/shims/fs.js',
    'node:path': 'path-browserify',
    path: 'path-browserify',
    'node:process': './src/shims/process.js',
    process: './src/shims/process.js',
    'node:perf_hooks': './src/shims/perf_hooks.js',
    perf_hooks: './src/shims/perf_hooks.js',
    colorette: './src/shims/colorette.js',
  },
  define: {
    'import.meta.url': '"file:///bundle/redocly-core.mjs"',
    __REDOCLY_VERSION__: JSON.stringify(coreVersion),
  },
});

writeFileSync(noticesFile, thirdPartyNotices(result.metafile));
printLargestInputs(result.metafile);

/**
 * openapi-core's browser build hard-codes `env = {}`, which would leave `resolve.http.headers[].envVariable`
 * empty. The replacement keeps browser mode (no plugin loading) but reads variables through the host.
 */
function browserEnvWithHostVariables() {
  return {
    name: 'browser-env-with-host-variables',
    setup(build) {
      build.onLoad({ filter: /@redocly\/openapi-core\/lib\/env\.js$/ }, () => ({
        contents: 'export const isBrowser = true; export const env = globalThis.process.env;',
        loader: 'js',
      }));
    },
  };
}

/** One section per bundled npm package (from the metafile) plus the vendored redocly-cli sources. */
function thirdPartyNotices(metafile) {
  const packageRoots = new Set();
  for (const input of Object.keys(metafile.inputs)) {
    const root = input.match(/^node_modules\/(?:@[^/]+\/[^/]+|[^/]+)/)?.[0];
    if (root) packageRoots.add(root);
  }
  const sections = [...packageRoots].sort().map((root) => {
    const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
    const licenseFile = ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENCE', 'license'].map((name) => path.join(root, name)).find(existsSync);
    const license = licenseFile
      ? readFileSync(licenseFile, 'utf8').trim()
      : `No license file is included in the package; its metadata declares ${pkg.license || 'an unspecified license'}.`;
    return section(`${pkg.name} ${pkg.version} (${pkg.license || 'see below'})`, license);
  });
  sections.push(section('@redocly/cli (vendored command sources)', readFileSync('vendor/redocly-cli/LICENSE', 'utf8').trim()));
  return ['THIRD-PARTY NOTICES', '', 'This product bundles the following third-party software:', ...sections, ''].join('\n');
}

function section(title, body) {
  return ['', '='.repeat(80), title, '-'.repeat(80), body].join('\n');
}

function printLargestInputs(metafile) {
  const inputs = Object.entries(metafile.inputs).sort((a, b) => b[1].bytes - a[1].bytes).slice(0, 12);
  console.log('largest inputs:');
  for (const [file, { bytes }] of inputs) console.log(`  ${(bytes / 1024).toFixed(0).padStart(6)} KB  ${file}`);
}
