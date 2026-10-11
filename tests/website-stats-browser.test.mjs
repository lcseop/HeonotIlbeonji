import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
const source = readFileSync(new URL('../public/js/website-stats.js', import.meta.url), 'utf8');
function page(beacon = true, prerendering = false) {
  const sent = [], fetched = [], listeners = {};
  let now = 10000, next = 0;
  const document = { prerendering, addEventListener: (name, fn) => { listeners[name] = fn; } };
  vm.runInNewContext(source, {
    document, Blob, crypto: { randomUUID: () => `event-${++next}` }, Date: { now: () => now },
    navigator: { sendBeacon: (url, body) => { if (beacon === 'throws') throw new Error('unavailable'); sent.push({ url, body }); return beacon; } },
    fetch: (url, options) => { fetched.push({ url, options }); return Promise.resolve({}); },
  });
  return { sent, fetched, listeners, time: value => { now = value; },
    click: (options = {}) => listeners.click({ target: { closest: () => ({}) }, button: 0, ...options }) };
}
test('view and phone clicks work independently of Google tags and suppress immediate double tap', async () => {
  const ui = page(); ui.click(); ui.click(); ui.time(12000); ui.click();
  assert.equal(ui.sent.length, 3);
  assert.deepEqual(await Promise.all(ui.sent.map(async row => JSON.parse(await row.body.text()).type)), ['view', 'phone', 'phone']);
  assert.equal(ui.fetched.length, 0);
});
test('failed beacon falls back to keepalive using the same anonymous event, without contact data', async () => {
  const ui = page(false);
  assert.equal(ui.fetched[0].options.keepalive, true);
  assert.equal(await ui.sent[0].body.text(), ui.fetched[0].options.body);
  assert.deepEqual(Object.keys(JSON.parse(ui.fetched[0].options.body)).sort(), ['id', 'type']);
  const broken = page('throws'); assert.doesNotThrow(() => broken.click());
  assert.equal(broken.fetched.length, 2);
});
test('non-phone, cancelled and modified clicks are ignored; prerender waits for activation', () => {
  const ui = page(true, true); assert.equal(ui.sent.length, 0);
  ui.listeners.prerenderingchange(); assert.equal(ui.sent.length, 1);
  for (const options of [{ target: { closest: () => null } }, { defaultPrevented: true }, { button: 1 }, { ctrlKey: true }]) ui.click(options);
  assert.equal(ui.sent.length, 1);
});
