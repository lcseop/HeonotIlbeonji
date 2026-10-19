import { ensureTables } from './admin.ts';
import { listDayFinances } from './admin-finances.ts';
import { ensureAcquisition, koreaDay, listWebsiteStats } from './website-stats.ts';

export const desktopTables = {
  requests: { title: '수거 신청서', table: 'pickup_requests', order: 'created_at DESC, id', columns: ['id','created_at','name','phone','address','amount','date','time_slot','pickup_method','message','status','reserved_time','admin_note','admin_note_updated_at','last_activity_at','traffic_source'] },
  day_notes: { title: '날짜별 메모', table: 'calendar_memos', order: 'date DESC', columns: ['date','content','updated_at'] },
  finances: { title: '하루 금액', table: 'calendar_finances', order: 'date DESC', columns: ['date','paid_amount','received_amount','updated_at'] },
  flags: { title: '단골·블랙리스트', table: 'customer_flags', order: 'updated_at DESC, phone', columns: ['phone','flag','updated_at'] },
  legacy_notes: { title: '이전 고객 메모', table: 'customer_memos', order: 'updated_at DESC, id', columns: ['id','phone','name','title','content','created_at','updated_at'] },
  daily_stats: { title: '일별 홈페이지 통계', table: 'website_daily_stats', order: 'date DESC', columns: ['date','views','phone_clicks','requests'] },
  sources: { title: '유입 경로 통계', table: 'website_source_stats', order: 'date DESC, source', columns: ['date','source','views','phone_clicks'] },
  visitors: { title: '일별 방문자 통계', table: 'website_daily_visitors', order: 'date DESC', columns: ['date','visitors'] },
} as const;
export async function prepareDesktop(db: D1Database) {
  await ensureTables(db); await ensureAcquisition(db);
  await listDayFinances(db, koreaDay().slice(0,7));
}
export function desktopQuery(url: URL) {
  const key = url.searchParams.get('table') || 'requests';
  const page = Number(url.searchParams.get('page') || '0');
  if (!Object.hasOwn(desktopTables, key) || !Number.isSafeInteger(page) || page < 0 || page > 100000) return null;
  return { key: key as keyof typeof desktopTables, page };
}
export async function readDesktopTable(db: D1Database, key: keyof typeof desktopTables, page: number) {
  const table = desktopTables[key];
  const [count, rows] = await Promise.all([
    db.prepare(`SELECT COUNT(*) AS total FROM ${table.table}`).all<{ total: number }>(),
    db.prepare(`SELECT ${table.columns.join(',')} FROM ${table.table} ORDER BY ${table.order} LIMIT 200 OFFSET ?`).bind(page * 200).all(),
  ]);
  return { table: key, title: table.title, page, pageSize: 200, total: count.results[0]?.total || 0, columns: table.columns, rows: rows.results,
    tables: Object.entries(desktopTables).map(([key, value]) => ({ key, title: value.title })) };
}
export async function desktopOverview(db: D1Database, month: string, settings: NodeJS.ProcessEnv) {
  await prepareDesktop(db);
  const today = koreaDay();
  const [stats, sources, visitors, totals, meta, todayStats] = await Promise.all([
    listWebsiteStats(db, month),
    db.prepare(`SELECT date, source, views, phone_clicks AS phoneClicks FROM website_source_stats WHERE date >= ? AND date <= ? ORDER BY date DESC, views DESC`).bind(`${month}-01`,`${month}-31`).all(),
    db.prepare('SELECT date, visitors FROM website_daily_visitors WHERE date >= ? AND date <= ? ORDER BY date DESC').bind(`${month}-01`,`${month}-31`).all(),
    db.prepare(`SELECT status, COUNT(*) AS count FROM pickup_requests GROUP BY status`).all(),
    db.prepare('SELECT started_at AS startedAt FROM website_acquisition_meta WHERE id=1').all<{startedAt:string}>(),
    db.prepare(`SELECT COALESCE((SELECT views FROM website_daily_stats WHERE date = ?),0) AS views,
      COALESCE((SELECT phone_clicks FROM website_daily_stats WHERE date = ?),0) AS phoneClicks,
      COALESCE((SELECT requests FROM website_daily_stats WHERE date = ?),0) AS requests,
      COALESCE((SELECT visitors FROM website_daily_visitors WHERE date = ?),0) AS visitors`).bind(today,today,today,today).all(),
  ]);
  return { ...stats, today, todaySummary: todayStats.results[0], sources: sources.results, visitors: visitors.results, requestCounts: totals.results,
    acquisitionStartedAt: meta.results[0]?.startedAt || today,
    health: { database: true, onlineForm: settings.PICKUP_DELIVERY_MODE === 'dashboard' && !!settings.TURNSTILE_SITE_KEY && !!settings.TURNSTILE_SECRET_KEY && !!settings.SITE_ORIGIN,
      pushConfigured: !!(settings.FCM_SERVICE_ACCOUNT_JSON || settings.FCM_SERVICE_ACCOUNT_JSON_BASE64) }, checkedAt: Date.now() };
}
