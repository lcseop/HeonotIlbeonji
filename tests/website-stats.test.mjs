import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { allowedWebsiteRequest, koreaDay, parseWebsiteEvent, recordWebsiteEvent, listWebsiteStats } from '../lib/website-stats.ts';
import { savePickup, deletePickup } from '../lib/admin.ts';
import { limitedJson } from '../lib/pickup.ts';

function database() {
  const sql = new DatabaseSync(':memory:');
  const db = { prepare(query) {
    const statement = sql.prepare(query);
    const bound = args => ({ run: async () => ({ meta: { changes: Number(statement.run(...args).changes) } }),
      all: async () => ({ results: statement.all(...args) }) });
    return { ...bound([]), bind: (...args) => bound(args) };
  }, async batch(statements) {
    sql.exec('BEGIN');
    try { const result = []; for (const statement of statements) result.push(await statement.run()); sql.exec('COMMIT'); return result; }
    catch (error) { sql.exec('ROLLBACK'); throw error; }
  } };
  return { db, sql, close: () => sql.close() };
}
const id = '9e438f23-aaaa-4bbb-8ccc-997979679fa1';
const now = new Date('2026-10-11T15:00:00Z'); // October 12 in Korea.
test('statistics validate public events and same-origin JSON, ignoring common bots', async () => {
  assert.equal(koreaDay(now), '2026-10-12');
  assert.equal(koreaDay(new Date(now.getTime() - 1)), '2026-10-11');
  assert.deepEqual(parseWebsiteEvent({ id, type: 'view' }), { id, type: 'view' });
  for (const input of [{ id: 'fake', type: 'view' }, { id, type: 'request' }, { id, type: 'unknown' }, { id, type: ['view'] }])
    assert.equal(parseWebsiteEvent(input), null);
  const request = (origin = 'https://1beonji.com', agent = 'browser') => new Request('https://1beonji.com/api/website-events', {
    method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json', 'User-Agent': agent }, body: '{}',
  });
  assert.equal(allowedWebsiteRequest(request(), 'https://1beonji.com'), true);
  assert.equal(allowedWebsiteRequest(request('https://other.example'), 'https://1beonji.com'), false);
  assert.equal(allowedWebsiteRequest(request(undefined, 'Googlebot'), 'https://1beonji.com'), false);
  assert.equal(allowedWebsiteRequest(request(), undefined), false);
  await assert.rejects(limitedJson(new Request('https://example.com', { method: 'POST', body: 'x'.repeat(257) }), 256));
});
test('duplicate delivery counts once, daily zeroes and month totals follow Korea dates', async () => {
  const { db, sql, close } = database();
  try {
    await recordWebsiteEvent(db, { id, type: 'view' }, new Date('2026-10-10T00:00:00Z'));
    await recordWebsiteEvent(db, { id, type: 'view' }, now);
    await recordWebsiteEvent(db, { id: crypto.randomUUID(), type: 'phone' }, now);
    await recordWebsiteEvent(db, { id: crypto.randomUUID(), type: 'view' }, new Date('2026-11-01T00:00:00Z'));
    const stats = await listWebsiteStats(db, '2026-10', now);
    assert.deepEqual(stats.totals, { views: 1, phoneClicks: 1, requests: 0 });
    assert.equal(stats.startedAt, '2026-10-10');
    assert.equal(stats.days.length, 3);
    assert.equal(stats.days[0].date, '2026-10-12');
    assert.deepEqual(stats.days[1], { date: '2026-10-11', views: 0, phoneClicks: 0, requests: 0 });
    assert.equal((await listWebsiteStats(db, '2026-09', now)).days.length, 0);
    assert.deepEqual(sql.prepare('PRAGMA table_info(website_stat_events)').all().map(row => row.name), ['id', 'date']);
  } finally { close(); }
});
const pickup = { name: '테스트', phone: '01000000000', address: '테스트', amount: '20kg', date: '2026-10-15', timeSlot: '오후', pickupMethod: '대면 수거', message: '' };
test('only accepted website submissions count; deleting requests preserves historical statistics', async () => {
  const { db, close } = database();
  try {
    await savePickup(db, pickup, 'manual');
    await savePickup(db, pickup, 'website', true);
    await savePickup(db, pickup, 'website', true);
    const month = koreaDay().slice(0, 7);
    assert.equal((await listWebsiteStats(db, month)).totals.requests, 1);
    await deletePickup(db, 'website');
    assert.equal((await listWebsiteStats(db, month)).totals.requests, 1);
  } finally { close(); }
});
test('failed transaction cannot leave an accepted request without its count', async () => {
  const { db, sql, close } = database();
  try {
    await listWebsiteStats(db, koreaDay().slice(0, 7));
    sql.exec(`CREATE TRIGGER reject_stats BEFORE INSERT ON website_stat_events BEGIN SELECT RAISE(ABORT, 'failure'); END`);
    await assert.rejects(savePickup(db, pickup, 'rollback', true));
    assert.equal(sql.prepare('SELECT COUNT(*) AS count FROM pickup_requests').get().count, 0);
    assert.equal(sql.prepare('SELECT COUNT(*) AS count FROM website_daily_stats').get().count, 0);
  } finally { close(); }
});
