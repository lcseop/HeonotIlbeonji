import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../public/js/phone-conversion.js', import.meta.url), 'utf8');
const phoneDestination = 'AW-18497578171/PUp8CI7rkZgdELvJqvRE';
function page(destination = phoneDestination, tracking = 'available') {
  const events = [], opened = [], timers = new Map();
  let click, nextTimer = 0;
  const window = { location: { assign: url => opened.push(url) } };
  if (tracking !== 'absent') window.gtag = (...args) => {
    if (tracking === 'throws') throw new Error('blocked');
    events.push(args);
  };
  vm.runInNewContext(source, {
    window,
    document: {
      querySelector: () => ({ content: destination }),
      addEventListener: (_, handler) => { click = handler; },
    },
    setTimeout: fn => { timers.set(++nextTimer, fn); return nextTimer; },
    clearTimeout: id => timers.delete(id),
  });
  function press(options = {}) {
    const event = {
      target: { closest: () => ({ getAttribute: () => 'tel:01048808259' }) },
      button: 0, defaultPrevented: false,
      preventDefault() { this.defaultPrevented = true; },
      ...options,
    };
    click?.(event);
    return event;
  }
  return { press, events, opened, timers };
}

test('phone click uses its own conversion and opens dialer only once after callback', () => {
  const ui = page();
  assert.equal(ui.press().defaultPrevented, true);
  ui.press();
  assert.equal(ui.events.length, 1);
  const [command, event, options] = ui.events[0];
  assert.equal(command, 'event');
  assert.equal(event, 'conversion');
  assert.equal(options.send_to, phoneDestination);
  assert.equal(options.value, 1.0);
  assert.equal(options.currency, 'KRW');
  assert.equal(ui.opened.length, 0);
  options.event_callback();
  options.event_callback();
  assert.deepEqual(ui.opened, ['tel:01048808259']);
  assert.equal(ui.timers.size, 0);
});

test('blocked tracking still opens dialer through timeout or exception', () => {
  const ui = page();
  ui.press();
  [...ui.timers.values()].forEach(fn => fn());
  ui.events[0][2].event_callback();
  assert.deepEqual(ui.opened, ['tel:01048808259']);
  const blocked = page(phoneDestination, 'throws');
  blocked.press();
  assert.deepEqual(blocked.opened, ['tel:01048808259']);
});

test('unconfigured, invalid or pickup destination never intercepts calls', () => {
  for (const destination of ['', 'invalid', 'AW-18497578171/Ftq6CNK4l5QdELvJqvRE']) {
    const ui = page(destination);
    assert.equal(ui.press().defaultPrevented, false);
    assert.equal(ui.events.length, 0);
  }
  const absent = page(phoneDestination, 'absent');
  assert.equal(absent.press().defaultPrevented, false);
});

test('other links, cancelled and modified clicks are not tracked', () => {
  const ui = page();
  for (const options of [
    { target: { closest: () => null } }, { defaultPrevented: true },
    { ctrlKey: true }, { metaKey: true }, { shiftKey: true }, { altKey: true }, { button: 1 },
  ]) ui.press(options);
  assert.equal(ui.events.length, 0);
});

test('homepage includes the phone listener once and retains the pickup listener', () => {
  const html = readFileSync(new URL('../public/html/index.html', import.meta.url), 'utf8');
  assert.equal(html.match(/src="\.\.\/js\/phone-conversion.js"/g)?.length, 1);
  assert.ok(html.includes('src="../js/pickup.js"'));
  assert.ok(html.includes(`name="google-ads-phone-click-conversion" content="${phoneDestination}"`));
});
