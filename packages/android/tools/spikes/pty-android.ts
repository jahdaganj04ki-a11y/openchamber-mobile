#!/usr/bin/env bun
/**
 * Runs a shell on a phone through the shared library of the pseudoterminal.
 *
 * This is the check of the terminal spike. The script opens the library, asks
 * it for a shell, and reads what the shell answers. The marker in the answer
 * says that the whole chain works on the device: the library loads, the
 * pseudoterminal opens, and the shell runs on the other side of it.
 */

import { dlopen, FFIType } from 'bun:ffi';

const library = process.env.PTY_LIBRARY;
if (!library) {
  throw new Error('PTY_LIBRARY must point at the shared library of the pseudoterminal');
}

const { symbols } = dlopen(library, {
  bun_pty_spawn: {
    args: [FFIType.cstring, FFIType.cstring, FFIType.cstring, FFIType.i32, FFIType.i32],
    returns: FFIType.i32,
  },
  bun_pty_read: {
    args: [FFIType.i32, FFIType.pointer, FFIType.i32],
    returns: FFIType.i32,
  },
  bun_pty_write: {
    args: [FFIType.i32, FFIType.pointer, FFIType.i32],
    returns: FFIType.i32,
  },
  bun_pty_get_pid: {
    args: [FFIType.i32],
    returns: FFIType.i32,
  },
  bun_pty_close: {
    args: [FFIType.i32],
    returns: FFIType.void,
  },
});

const marker = 'openchamber-pty-ok';
const shell = process.env.PTY_SHELL ?? '/system/bin/sh';

const handle = symbols.bun_pty_spawn(`${shell} -c "echo ${marker}"`, process.cwd(), '', 80, 24);
if (handle <= 0) {
  console.error('the pseudoterminal did not start');
  process.exit(1);
}

const pid = symbols.bun_pty_get_pid(handle);
console.log(`the pseudoterminal started the shell with the pid ${pid}`);

const buffer = new Uint8Array(4096);
const decoder = new TextDecoder();
let output = '';

while (!output.includes(marker)) {
  const read = symbols.bun_pty_read(handle, buffer, buffer.length);
  if (read === -2) {
    break;
  }
  if (read <= 0) {
    await Bun.sleep(100);
    continue;
  }
  output += decoder.decode(buffer.subarray(0, read));
}

symbols.bun_pty_close(handle);

console.log(`the shell said: ${output.trim()}`);
if (!output.includes(marker)) {
  console.error('the shell did not answer');
  process.exit(1);
}

console.log('the pseudoterminal runs a shell on Android');
