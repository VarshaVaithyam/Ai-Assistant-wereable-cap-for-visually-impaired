import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalizeHost, readSettings } from './settings.mjs';
test('normalizes base camera addresses', () => {
  assert.equal(normalizeHost(' http://192.168.4.1/ '), 'http://192.168.4.1');
  assert.equal(normalizeHost('https://camera.local:8443'), 'https://camera.local:8443');
});
test('rejects paths, credentials, queries, unsupported schemes and fragments', () => {
  for (const address of ['192.168.4.1', 'http://cap/stream', 'http://a:b@cap', 'ftp://cap', 'http://cap?x=1', 'http://cap#x']) {
    assert.throws(() => normalizeHost(address));
  }
});
test('corrupt or unavailable storage falls back without inventing a connection', () => {
  assert.deepEqual(readSettings({ getItem: () => '{' }), { host: 'http://192.168.4.1', audio: true });
  assert.deepEqual(readSettings({ getItem: () => { throw new Error('denied'); } }), { host: 'http://192.168.4.1', audio: true });
});
test('saved preferences retain audio choice and normalize the host', () => {
  assert.deepEqual(readSettings({ getItem: () => JSON.stringify({host: 'http://192.168.4.1/', audio: false}) }), {host: 'http://192.168.4.1', audio: false});
});
