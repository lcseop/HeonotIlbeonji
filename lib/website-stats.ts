export type WebsiteEvent = { id: string; type: 'view' | 'phone' };
export type WebsiteDay = { date: string; views: number; phoneClicks: number; requests: number };
const prepared = new WeakSet<D1Database>();
export function koreaDay(now = new Date()) { return new Date(now.getTime() + 9 * 3600000).toISOString().slice(0, 10); }
export function parseWebsiteEvent(data: Record<string, unknown>): WebsiteEvent | null {
  if (typeof data.id !== 'string' || !/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/i.test(data.id) ||
      (data.type !== 'view' && data.type !== 'phone')) return null;
  return { id: data.id, type: data.type };
}
export function allowedWebsiteRequest(request: Request, origin?: string) {
  try {
    if (!origin || request.headers.get('origin') !== new URL(origin).origin) return false;
  } catch { return false; }
  return request.headers.get('content-type')?.startsWith('application/json') === true &&
    !/bot|crawler|spider|headless|lighthouse|pagespeed/i.test(request.headers.get('user-agent') || '');
}
export async function ensureWebsiteStats(db: D1Database, now = new Date()) {
  if (prepared.has(db)) return;
  await db.prepare(`CREATE TABLE IF NOT EXISTS website_daily_stats (
    date TEXT PRIMARY KEY, views INTEGER NOT NULL DEFAULT 0,
    phone_clicks INTEGER NOT NULL DEFAULT 0, requests INTEGER NOT NULL DEFAULT 0
  )`).run();
  // Random, per-action IDs only: no visitor identifiers, IPs, URLs or contact information.
  await db.prepare(`CREATE TABLE IF NOT EXISTS website_stat_events (
    id TEXT PRIMARY KEY, date TEXT NOT NULL
  )`).run();
  await db.prepare(`CREATE TABLE IF NOT EXISTS website_stats_meta (
    id INTEGER PRIMARY KEY CHECK(id = 1), started_at TEXT NOT NULL
  )`).run();
  await db.prepare('INSERT OR IGNORE INTO website_stats_meta (id, started_at) VALUES (1, ?)').bind(koreaDay(now)).run();
  await db.prepare('DELETE FROM website_stat_events WHERE date < ?').bind(koreaDay(new Date(now.getTime() - 90 * 86400000))).run();
  prepared.add(db);
}
export function websiteStatStatements(db: D1Database, id: string, type: 'view' | 'phone' | 'request', now = new Date()) {
  const column = { view: 'views', phone: 'phone_clicks', request: 'requests' }[type];
  const date = koreaDay(now);
  return [
    db.prepare(`INSERT INTO website_daily_stats (date, ${column})
      SELECT ?, 1 WHERE NOT EXISTS (SELECT 1 FROM website_stat_events WHERE id = ?)
      ON CONFLICT(date) DO UPDATE SET ${column} = ${column} + 1`).bind(date, id),
    db.prepare('INSERT OR IGNORE INTO website_stat_events (id, date) VALUES (?, ?)').bind(id, date),
  ];
}
export async function recordWebsiteEvent(db: D1Database, event: WebsiteEvent, now = new Date()) {
  await ensureWebsiteStats(db, now);
  await db.batch(websiteStatStatements(db, event.id, event.type, now));
}
export async function listWebsiteStats(db: D1Database, month: string, now = new Date()) {
  await ensureWebsiteStats(db, now);
  const stored = (await db.prepare(`SELECT date, views, phone_clicks AS phoneClicks, requests
    FROM website_daily_stats WHERE date >= ? AND date <= ? ORDER BY date DESC`)
    .bind(`${month}-01`, `${month}-31`).all<WebsiteDay>()).results;
  const meta = (await db.prepare('SELECT started_at AS startedAt FROM website_stats_meta WHERE id = 1')
    .all<{ startedAt: string }>()).results[0];
  const startedAt = meta?.startedAt || koreaDay(now);
  const today = koreaDay(now);
  const days: WebsiteDay[] = [];
  const end = new Date(Date.UTC(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)).getUTCDate();
  for (let day = end; day >= 1; day--) {
    const date = `${month}-${String(day).padStart(2, '0')}`;
    if (date < startedAt || date > today) continue;
    days.push(stored.find(row => row.date === date) || { date, views: 0, phoneClicks: 0, requests: 0 });
  }
  return { month, startedAt, days, totals: days.reduce((sum, day) => ({
    views: sum.views + day.views, phoneClicks: sum.phoneClicks + day.phoneClicks, requests: sum.requests + day.requests,
  }), { views: 0, phoneClicks: 0, requests: 0 }) };
}
