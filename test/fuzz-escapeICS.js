#!/usr/bin/env node
/* eslint-disable no-console */
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const html = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const match = html.match(/function escapeICS\(s\)\{[\s\S]*?\n  \}/);

if (!match) {
  throw new Error('escapeICS function could not be located');
}

const context = {};
vm.createContext(context);
vm.runInContext(`${match[0]};`, context);

if (typeof context.escapeICS !== 'function') {
  throw new Error('escapeICS was not loaded as a function');
}

const escapeICS = context.escapeICS;

function check(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

// Directed examples for critical edge cases.
check(escapeICS('\r') === '\\n', 'Lone carriage return should become \\n');
check(escapeICS('\n') === '\\n', 'Lone newline should become \\n');
check(escapeICS('\r\n') === '\\n', 'CRLF should become single \\n');

// Ensure comma, semicolon and backslash are escaped properly.
check(escapeICS(',;\\') === '\\,\\;\\\\', 'Special punctuation escaping failed');

// Fuzz random inputs for additional coverage.
const alphabet = ['\r', '\n', '\\', ',', ';', 'a', 'b', 'c', '1', '2', ' ', '\t'];
for (let i = 0; i < 5000; i++) {
  const length = Math.floor(Math.random() * 24);
  let input = '';
  for (let j = 0; j < length; j++) {
    input += alphabet[Math.floor(Math.random() * alphabet.length)];
  }

  const output = escapeICS(input);

  check(!/\r/.test(output), 'Output should not contain raw carriage returns');
  check(!/,/.test(output.replace(/\\,/g, '')), 'Commas must be escaped');
  check(!/;/.test(output.replace(/\\;/g, '')), 'Semicolons must be escaped');
  const cleaned = output
    .replace(/\\n/g, '')
    .replace(/\\,/g, '')
    .replace(/\\;/g, '')
    .replace(/\\\\/g, '');
  check(!/\\/.test(cleaned), 'Backslashes must be part of escape sequences');
}

console.log('escapeICS fuzz tests passed.');
