#!/usr/bin/env bun
/**
 * Spike 0.1: what a compiled Bun binary can do on an Android phone.
 *
 * The OpenChamber server needs five things from the runtime it runs on: an
 * HTTP server on the loopback interface, file access, a child process, a
 * SQLite database and a WebSocket. This program tries all five and prints one
 * line per check. It exits with 1 when a check failed, so CI stops there.
 *
 *   bun build --compile --target=bun-linux-aarch64-android \
 *     packages/android/tools/spikes/bun-android.ts --outfile bun-android
 */

import { Database } from 'bun:sqlite';
import { spawn } from 'node:child_process';
import { mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { join } from 'node:path';

const workDir = process.env.SPIKE_WORK_DIR ?? '/data/local/tmp/bun-spike';
const shell = process.env.SPIKE_SHELL ?? '/system/bin/sh';
const failures: string[] = [];

async function check(name: string, run: () => Promise<string>): Promise<void> {
  try {
    const detail = await run();
    console.log(`ok    ${name}${detail ? ` (${detail})` : ''}`);
  } catch (error) {
    const reason = error instanceof Error ? error.message : String(error);
    console.log(`FAIL  ${name}: ${reason}`);
    failures.push(name);
  }
}

await mkdir(workDir, { recursive: true });

await check('files', async () => {
  const path = join(workDir, 'hello.txt');
  await writeFile(path, 'written on the device', 'utf8');
  const content = await readFile(path, 'utf8');
  await rm(path, { force: true });
  if (content !== 'written on the device') {
    throw new Error(`read back ${JSON.stringify(content)}`);
  }
  return workDir;
});

await check('child process', async () => {
  const child = spawn(shell, ['-c', 'echo spawned'], { stdio: ['ignore', 'pipe', 'pipe'] });
  let output = '';
  child.stdout.on('data', (chunk) => {
    output += String(chunk);
  });
  const code = await new Promise<number>((resolve, reject) => {
    child.on('error', reject);
    child.on('close', (exitCode) => resolve(exitCode ?? -1));
  });
  if (code !== 0) {
    throw new Error(`shell exited with ${code}`);
  }
  if (!output.includes('spawned')) {
    throw new Error(`shell said ${JSON.stringify(output)}`);
  }
  return shell;
});

await check('sqlite', async () => {
  const path = join(workDir, 'spike.db');
  const database = new Database(path, { create: true });
  database.run('CREATE TABLE IF NOT EXISTS entries (id INTEGER PRIMARY KEY, name TEXT)');
  database.run('INSERT INTO entries (name) VALUES (?)', ['written']);
  const row = database.query<{ name: string }, []>('SELECT name FROM entries LIMIT 1').get();
  database.close();
  await rm(path, { force: true });
  if (!row || row.name !== 'written') {
    throw new Error(`row was ${JSON.stringify(row)}`);
  }
  return path;
});

await check('http and websocket', async () => {
  let socketOpened = false;
  const server = Bun.serve({
    hostname: '127.0.0.1',
    port: 0,
    fetch(request, serverInstance) {
      const url = new URL(request.url);
      if (url.pathname === '/ws') {
        if (serverInstance.upgrade(request)) {
          return undefined as unknown as Response;
        }
        return new Response('upgrade expected', { status: 400 });
      }
      return new Response('served on the device', { status: 200 });
    },
    websocket: {
      open() {
        socketOpened = true;
      },
      message(socket, message) {
        socket.send(`echo ${message}`);
      },
    },
  });

  try {
    const response = await fetch(`http://127.0.0.1:${server.port}/`);
    const body = await response.text();
    if (body !== 'served on the device') {
      throw new Error(`body was ${JSON.stringify(body)}`);
    }

    const answer = await new Promise<string>((resolve, reject) => {
      const socket = new WebSocket(`ws://127.0.0.1:${server.port}/ws`);
      const timer = setTimeout(() => reject(new Error('no answer in 5 seconds')), 5_000);
      socket.addEventListener('open', () => socket.send('ping'));
      socket.addEventListener('message', (event) => {
        clearTimeout(timer);
        resolve(String(event.data));
      });
      socket.addEventListener('error', () => {
        clearTimeout(timer);
        reject(new Error('the socket failed'));
      });
    });
    if (answer !== 'echo ping') {
      throw new Error(`the socket said ${JSON.stringify(answer)}`);
    }
    if (!socketOpened) {
      throw new Error('the server never saw the socket open');
    }
    return `port ${server.port}`;
  } finally {
    server.stop(true);
  }
});

if (failures.length > 0) {
  console.log(`FAILED ${failures.length} check(s): ${failures.join(', ')}`);
  process.exit(1);
}

console.log('all checks passed');
