#!/usr/bin/env node
/**
 * The OpenChamber server as the phone runs it.
 *
 * The app starts this as a child process on the loopback interface, reads the
 * port from the line this script prints, and talks to the server over
 * `http://127.0.0.1:<port>`.
 *
 * The option set is the CLI's foreground mode without the daemon machinery:
 * no pid file, no tunnel, no generated password, no signal handlers. The
 * app owns the process life cycle, and the server owns the engine.
 */

import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';

import { startWebUiServer } from '../../web/server/index.js';

const requestedPort = Number.parseInt(process.env.OPENCHAMBER_PORT || '', 10);

/**
 * The server reads the catalog of the guest panels the app ships from a
 * directory next to its own modules. A compiled binary has no directory next
 * to it, so the phone keeps the catalog in its own storage. The phone ships
 * no guest panels yet, and an empty catalog says exactly that.
 */
const builtInExtensionsDir = await prepareBuiltInExtensionsDir();

async function prepareBuiltInExtensionsDir() {
  const root = process.env.OPENCHAMBER_BUILTIN_EXTENSIONS_DIR
    || path.join(process.env.OPENCHAMBER_DATA_DIR || process.cwd(), 'built-in-extensions');
  await mkdir(root, { recursive: true });
  const registry = path.join(root, 'registry.json');
  const catalog = `${JSON.stringify({ version: 1, extensions: [] }, null, 2)}\n`;
  await writeFile(registry, catalog, { flag: 'wx' }).catch(() => {});
  return root;
}

const server = await startWebUiServer({
  port: Number.isFinite(requestedPort) && requestedPort >= 0 ? requestedPort : 0,
  host: '127.0.0.1',
  apiOnly: true,
  attachSignals: false,
  exitOnShutdown: false,
  uiPassword: null,
  builtInExtensionsDir,
});

const address = server.httpServer?.address?.();
const port = typeof address === 'object' && address ? address.port : server.getPort?.();

// The app reads this line to learn the port. It is the only line on stdout
// that is not a server log line.
process.stdout.write(`${JSON.stringify({ type: 'openchamber.listening', port })}\n`);

const stop = async () => {
  try {
    await server.stop({ exitProcess: false });
  } finally {
    process.exit(0);
  }
};

process.on('SIGTERM', stop);
process.on('SIGINT', stop);
