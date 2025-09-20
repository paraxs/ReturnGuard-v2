const assert = require('assert');
const fs = require('fs');
const path = require('path');

const src = fs.readFileSync(path.join(__dirname, '..', 'index.html'), 'utf8');
const match = src.match(/function escapeICS\(s\)\{[\s\S]*?\n\s+\}/);
if (!match){
  throw new Error('escapeICS function definition not found');
}
const escapeICS = eval('(' + match[0] + ')');

const expectedEscape = (s) => String(s)
  .replace(/\\/g, '\\\\')
  .replace(/\r\n/g, '\\n')
  .replace(/\n/g, '\\n')
  .replace(/\r/g, '\\n')
  .replace(/,/g, '\\,')
  .replace(/;/g, '\\;');

const deterministicCases = [
  { input: '\r', expect: '\\n' },
  { input: 'abc', expect: 'abc' },
  { input: 'a\n b', expect: 'a\\n b' },
  { input: 'a\r\nb', expect: 'a\\nb' },
  { input: 'x\rb', expect: 'x\\nb' },
  { input: 'comma,semicolon;', expect: 'comma\\,semicolon\\;' },
  { input: '\\backslash', expect: '\\\\backslash' },
];

let sawLoneCR = false;
deterministicCases.forEach(({ input, expect }) => {
  assert.strictEqual(escapeICS(input), expect, `escapeICS failed for ${JSON.stringify(input)}`);
  if (input.includes('\r') && !input.includes('\n')) sawLoneCR = true;
});

function randomString(){
  const length = Math.floor(Math.random() * 20);
  const alphabet = '\r\n,;\\ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  let out = '';
  for (let i = 0; i < length; i++){
    out += alphabet.charAt(Math.floor(Math.random() * alphabet.length));
  }
  return out;
}

for (let i = 0; i < 5000; i++){
  const input = randomString();
  const actual = escapeICS(input);
  const expected = expectedEscape(input);
  assert.strictEqual(actual, expected, `Mismatch for ${JSON.stringify(input)}`);
  if (input.includes('\r') && !input.includes('\n')){
    sawLoneCR = true;
    assert(actual.includes('\\n'), 'Lone CR must map to \\n');
  }
}

assert(sawLoneCR, 'Fuzz inputs never produced a lone CR case');

console.log('escapeICS fuzz tests passed');
