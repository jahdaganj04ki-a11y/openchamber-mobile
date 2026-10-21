#!/usr/bin/env bun
/**
 * Compiles the OpenCode engine for Android.
 *
 * The engine is built from a read-only checkout of the opencode repository.
 * Upstream's own release build knows how to compile the CLI, but its target
 * list holds the desktop platforms only. This script does the same work with
 * the Android target and with the parts that do not exist on a phone turned
 * off:
 *
 * - The bundled web interface is not needed. The phone is the interface, and
 *   the archive is replaced with an empty one.
 * - The file watcher has no native build for Android. The engine gets the
 *   changes over the wire from the app instead.
 * - The terminal of the engine is not used. The server brings its own.
 *
 * The plugin runtime is built the way upstream builds it: the specifiers of
 * the runtime packages are resolved in the checkout and written into the
 * bundle as static requires, so the engine finds them in the binary.
 *
 * Usage:
 *
 *   OPENCODE_CLI_DIR=<checkout>/packages/cli \
 *   OPENCODE_OUTFILE=out/opencode-android \
 *   bun packages/android/tools/build-opencode-android.ts
 */

import { resolve } from 'node:path';
import type { BunPlugin } from 'bun';

const cliDir = process.env.OPENCODE_CLI_DIR;
if (!cliDir) {
  throw new Error('OPENCODE_CLI_DIR must point at the packages/cli folder of the checkout');
}

const target = process.env.OPENCODE_TARGET ?? 'bun-linux-aarch64-android';
const outfile = resolve(process.env.OPENCODE_OUTFILE ?? 'out/opencode-android');
const version = process.env.OPENCODE_VERSION ?? '2.0.32';
const channel = process.env.OPENCODE_CHANNEL ?? 'prod';

process.chdir(cliDir);

const appAssetsPlugin: BunPlugin = {
  name: 'opencode-app-assets',
  setup(build) {
    build.onResolve({ filter: /^virtual:opencode-app-assets$/ }, () => ({
      path: 'opencode-app-assets',
      namespace: 'opencode',
    }));
    build.onLoad({ filter: /^opencode-app-assets$/, namespace: 'opencode' }, () => ({
      loader: 'js',
      contents: 'export default () => new Uint8Array(0)',
    }));
  },
};

const parcelWatcherPlugin: BunPlugin = {
  name: 'opencode-parcel-watcher',
  setup(build) {
    build.onLoad({ filter: /filesystem[/\\]watcher-binding\.ts$/ }, () => ({
      loader: 'js',
      contents: 'export default () => ({ subscribe: () => () => {}, unsubscribe: () => {} })',
    }));
  },
};

const opencodePtyPlugin: BunPlugin = {
  name: 'opencode-pty-binary',
  setup(build) {
    build.onLoad({ filter: /persistent-pty[/\\]pty-binding\.ts$/ }, () => ({
      loader: 'js',
      contents: 'export default undefined',
    }));
  },
};

/**
 * The plugin runtime resolves its packages with `Bun.resolveSync` and a
 * name that only exists at runtime, so the bundler cannot follow it. The
 * build resolves the names in the checkout and writes them into the bundle
 * as static requires, the way upstream does.
 */
const pluginRuntimePlugin = await createPluginRuntimePlugin(cliDir);

async function createPluginRuntimePlugin(from: string): Promise<BunPlugin> {
  const entry = Bun.resolveSync('@opencode/plugin/runtime-modules', from);
  const { discoverPluginRuntimeSpecifiers, pluginRuntimeLoaderCode } = await import(entry);
  const entries = discoverPluginRuntimeSpecifiers();
  const source = [
    'export const resolveHostPackageRoots = () => []',
    'const modules = {',
    ...[...entries.keys()].map(
      (specifier) => `  ${JSON.stringify(specifier)}: ${pluginRuntimeLoaderCode(specifier, entries)},`,
    ),
    '}',
    'export const loadRuntimeModules = () => modules',
  ].join('\n');

  return {
    name: 'opencode-plugin-runtime',
    setup(build) {
      build.onLoad({ filter: /plugin[/\\]src[/\\]runtime-modules\.ts$/ }, () => ({
        loader: 'ts',
        contents: source,
      }));
      build.onLoad({ filter: /[/\\]internal[/\\]httpApi(?:Scalar|Swagger)\.js$/ }, () => ({
        loader: 'js',
        contents:
          'export const css = ""; export const javascript = \'document.body.textContent = "Scalar/Swagger UI assets are not bundled in OpenCode"\'',
      }));
    },
  };
}

/** The terminal of the engine is written for Solid. The build needs its transform. */
const solidPlugin = await createSolidPlugin(cliDir);

async function createSolidPlugin(from: string): Promise<BunPlugin> {
  const entry = Bun.resolveSync('@opentui/solid/bun-plugin', from);
  const { createSolidTransformPlugin } = await import(entry);
  return createSolidTransformPlugin();
}

const result = await Bun.build({
  entrypoints: ['./src/index.ts'],
  tsconfig: './tsconfig.json',
  plugins: [appAssetsPlugin, solidPlugin, parcelWatcherPlugin, opencodePtyPlugin, pluginRuntimePlugin],
  external: ['node-gyp'],
  format: 'esm',
  minify: true,
  bytecode: true,
  sourcemap: 'none',
  splitting: true,
  compile: {
    autoloadBunfig: false,
    autoloadDotenv: false,
    autoloadTsconfig: true,
    autoloadPackageJson: true,
    target: target as Bun.Build.CompileTarget,
    outfile,
    execArgv: [
      '--smol',
      `--user-agent=opencode/${channel}/${version}/cli`,
      '--use-system-ca',
      '--no-warnings',
      '--',
    ],
  },
  define: {
    OPENCODE_VERSION: `'${version}'`,
    OPENCODE_CLI_NAME: "'opencode'",
    OPENCODE_CHANNEL: `'${channel}'`,
    OPENCODE_ARTIFACT: "'cli'",
    OPENCODE_LIBC: 'undefined',
    FFF_LIBC: 'undefined',
  },
});

if (!result.success) {
  for (const log of result.logs) console.error(log);
  process.exit(1);
}

console.log(`built ${outfile} for ${target}`);
