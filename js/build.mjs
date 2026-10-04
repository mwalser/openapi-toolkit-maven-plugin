// Bundles the plugin's JavaScript into two ES modules that GraalJS can evaluate, plus the license notices of
// everything they contain:
//   redocly-core.mjs  @redocly/openapi-core, the vendored CLI commands and the command layer of every goal
//   redocly-docs.mjs  Redoc with React for build-docs, evaluated into the same context when that goal first runs
//
//   node build.mjs [core bundle] [docs bundle] [notices file]
//   (defaults: dist/redocly-core.mjs, dist/redocly-docs.mjs, dist/THIRD-PARTY-NOTICES.txt)
import * as esbuild from 'esbuild';
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import * as path from 'node:path';

const coreVersion = packageVersion('@redocly/openapi-core');
const pomVersion = readFileSync('../pom.xml', 'utf8').match(/<redocly\.version>([^<]+)<\/redocly\.version>/)?.[1];
if (pomVersion !== coreVersion) {
  throw new Error(`Redocly version mismatch: package.json has ${coreVersion}, pom.xml has ${pomVersion}`);
}
const redocVersion = packageVersion('redoc');
// The page loads redoc.standalone.js from Redocly's CDN with this subresource integrity hash, as the CLI does.
const redocStandaloneSri = 'sha384-' + createHash('sha384').update(readFileSync('node_modules/redoc/bundles/redoc.standalone.js')).digest('base64');

const [coreFile = 'dist/redocly-core.mjs', docsFile = 'dist/redocly-docs.mjs', noticesFile = 'dist/THIRD-PARTY-NOTICES.txt'] = process.argv.slice(2);
for (const file of [coreFile, docsFile, noticesFile]) mkdirSync(path.dirname(file), { recursive: true });

const core = await build({
  entry: 'src/index.js',
  outfile: coreFile,
  define: { __REDOCLY_VERSION__: JSON.stringify(coreVersion) },
});
const docs = await build({
  entry: 'src/docs.js',
  outfile: docsFile,
  alias: {
    // The Node library build: server-side rendering needs it; the `browser` field names the build for pages.
    // Absolute paths, because esbuild applies a package's `browser` field remapping to relative alias targets.
    redoc: path.resolve('node_modules/redoc/bundles/redoc.lib.js'),
    // the server build; the browser build inserts its styles into a document
    'styled-components': path.resolve('node_modules/styled-components/dist/styled-components.esm.js'),
    http: './src/shims/empty.js',
    https: './src/shims/empty.js',
    os: './src/shims/empty.js',
    stream: './src/shims/stream.js',
    'node:stream': './src/shims/stream.js',
  },
  define: { __REDOC_VERSION__: JSON.stringify(redocVersion), __REDOC_STANDALONE_SRI__: JSON.stringify(redocStandaloneSri) },
});
writeFileSync(noticesFile, thirdPartyNotices([core.metafile, docs.metafile]));

async function build({ entry, outfile, alias = {}, define = {} }) {
  const result = await esbuild.build({
    entryPoints: [entry],
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
      ...alias,
    },
    define: {
      'import.meta.url': JSON.stringify(`file:///bundle/${path.basename(outfile)}`),
      'process.env.NODE_ENV': '"production"', // React ships a development build that checks this at load
      ...define,
    },
  });
  printLargestInputs(outfile, result.metafile);
  return result;
}

function packageVersion(name) {
  return JSON.parse(readFileSync(`node_modules/${name}/package.json`, 'utf8')).version;
}

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

/** One section per bundled npm package (from the metafiles) plus the vendored redocly-cli sources. */
function thirdPartyNotices(metafiles) {
  const packageRoots = new Set();
  for (const metafile of metafiles) {
    for (const input of Object.keys(metafile.inputs)) {
      // the innermost package directory: redoc brings its own copy of @redocly/openapi-core
      const root = input.match(/^(?:.*\/)?node_modules\/(?:@[^/]+\/[^/]+|[^/]+)/)?.[0];
      if (root && !input.startsWith('(disabled)')) packageRoots.add(root);
    }
  }
  const listed = new Set(); // an npm alias (node_modules/ajv -> @redocly/ajv) must not list a package twice
  const sections = [...packageRoots].sort().flatMap((root) => {
    const pkg = JSON.parse(readFileSync(path.join(root, 'package.json'), 'utf8'));
    if (listed.has(`${pkg.name}@${pkg.version}`)) return [];
    listed.add(`${pkg.name}@${pkg.version}`);
    const licenseFile = ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENCE', 'license'].map((name) => path.join(root, name)).find(existsSync);
    const license = licenseFile
      ? readFileSync(licenseFile, 'utf8').replace(/\r\n?/g, '\n').trim() // git normalizes line endings; the comparison in CI must see the same bytes
      : `No license file is included in the package; its metadata declares ${pkg.license || 'an unspecified license'}.`;
    return [section(`${pkg.name} ${pkg.version} (${pkg.license || 'see below'})`, license)];
  });
  sections.push(section('@redocly/cli (vendored command sources)', readFileSync('vendor/redocly-cli/LICENSE', 'utf8').trim()));
  return ['THIRD-PARTY NOTICES', '', 'This product bundles the following third-party software:', ...sections, ''].join('\n');
}

function section(title, body) {
  return ['', '='.repeat(80), title, '-'.repeat(80), body].join('\n');
}

function printLargestInputs(outfile, metafile) {
  const inputs = Object.entries(metafile.inputs).sort((a, b) => b[1].bytes - a[1].bytes).slice(0, 12);
  console.log(`largest inputs of ${path.basename(outfile)}:`);
  for (const [file, { bytes }] of inputs) console.log(`  ${(bytes / 1024).toFixed(0).padStart(6)} KB  ${file}`);
}
