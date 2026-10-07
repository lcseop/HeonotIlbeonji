import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../public/js/pickup.js', import.meta.url), 'utf8');
async function page(response, tracking = 'available') {
  const events = [];
  const listeners = {};
  const classes = new Set();
  const notice = { classList: { toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); }, contains: name => classes.has(name) } };
  const button = {};
  const form = {
    elements: { date: { value: '2026-10-08', addEventListener() {}, focus() {} }, consent: { checked: true } },
    querySelector: () => button, addEventListener: (name, fn) => { listeners[name] = fn; },
    reportValidity: () => true, setAttribute() {}, reset() {},
  };
  const window = { turnstile: {
    render(target, options) { options.callback('verified'); return 1; }, remove() {}, reset() {},
  } };
  if (tracking !== 'absent') window.gtag = (...args) => {
    if (tracking === 'throws') throw new Error('blocked');
    events.push(args);
  };
  const context = {
    window, Date, AbortSignal, setTimeout: () => 1, clearTimeout() {},
    FormData: class { get() { return 'fixture'; } },
    document: {
      querySelector: selector => selector === '#pickupForm' ? form : notice,
      createElement: () => ({}), head: { append: script => script.onload() },
    },
    fetch: async (url, options) => options.method === 'POST' ? response :
      { ok: true, json: async () => ({ enabled: true, siteKey: 'test' }) },
  };
  vm.runInNewContext(source, context);
  // Complete initialization without contacting any external service.
  await new Promise(resolve => setImmediate(resolve));
  const submit = () => listeners.submit({ preventDefault() {} });
  return { submit, events, button, notice };
}

test('accepted request records the supplied conversion once, even with repeated submit', async () => {
  const ui = await page({ ok: true, json: async () => ({ accepted: true, message: '접수 완료' }) });
  await ui.submit();
  await ui.submit();
  assert.deepEqual(JSON.parse(JSON.stringify(ui.events)), [['event', 'conversion', {
    send_to: 'AW-18497578171/Ftq6CNK4l5QdELvJqvRE',
  }]]);
  assert.equal(ui.button.textContent, '신청 전달 완료');
});

test('rejected or uncertain requests do not record a conversion', async () => {
  for (const result of [{ accepted: false }, { uncertain: true }, { accepted: true }]) {
    const ui = await page({ ok: false, json: async () => result });
    await ui.submit();
    assert.equal(ui.events.length, 0);
  }
});

test('missing or blocked tracking does not turn an accepted request into a failure', async () => {
  for (const tracking of ['absent', 'throws']) {
    const ui = await page({ ok: true, json: async () => ({ accepted: true, message: '접수 완료' }) }, tracking);
    await ui.submit();
    assert.equal(ui.notice.textContent, '접수 완료');
    assert.equal(ui.button.textContent, '신청 전달 완료');
  }
});
