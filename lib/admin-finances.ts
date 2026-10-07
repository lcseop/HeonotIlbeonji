import { ensureTables } from './admin.ts';
import { validPlannerDate } from './admin-planner.ts';

export type DayFinance = { date: string; paidAmount: number; receivedAmount: number; content: string };
export function validFinanceMonth(value: unknown): value is string {
  return typeof value === 'string' && /^\d{4}-\d{2}$/.test(value) && validPlannerDate(`${value}-01`);
}
export function parseDayFinance(data: Record<string, unknown>): DayFinance | null {
  if (!validPlannerDate(data.date) || typeof data.content !== 'string' || data.content.length > 2000 ||
    /[\u0000-\u0008\u000b-\u001f\u007f]/.test(data.content)) return null;
  for (const amount of [data.paidAmount, data.receivedAmount])
    if (typeof amount !== 'number' || !Number.isSafeInteger(amount) || amount < 0 || amount > 999999999999) return null;
  return { date: data.date, paidAmount: data.paidAmount as number,
    receivedAmount: data.receivedAmount as number, content: data.content.trim() };
}
async function ensureFinances(db: D1Database) {
  await ensureTables(db);
  await db.prepare(`CREATE TABLE IF NOT EXISTS calendar_finances (
    date TEXT PRIMARY KEY, paid_amount INTEGER NOT NULL CHECK(paid_amount >= 0),
    received_amount INTEGER NOT NULL CHECK(received_amount >= 0), updated_at INTEGER NOT NULL
  )`).run();
}
export async function listDayFinances(db: D1Database, month: string) {
  await ensureFinances(db);
  return (await db.prepare(`SELECT date, paid_amount AS paidAmount, received_amount AS receivedAmount,
    updated_at AS updatedAt FROM calendar_finances WHERE date >= ? AND date <= ? ORDER BY date`)
    .bind(`${month}-01`, `${month}-31`).all<{ date: string; paidAmount: number; receivedAmount: number; updatedAt: number }>()).results;
}
export async function saveDayFinance(db: D1Database, record: DayFinance) {
  await ensureFinances(db);
  const now = Date.now();
  const money = db.prepare(`INSERT INTO calendar_finances (date, paid_amount, received_amount, updated_at)
    VALUES (?, ?, ?, ?) ON CONFLICT(date) DO UPDATE SET paid_amount = excluded.paid_amount,
    received_amount = excluded.received_amount, updated_at = excluded.updated_at`)
    .bind(record.date, record.paidAmount, record.receivedAmount, now);
  const memo = record.content ? db.prepare(`INSERT INTO calendar_memos (date, content, updated_at) VALUES (?, ?, ?)
    ON CONFLICT(date) DO UPDATE SET content = excluded.content, updated_at = excluded.updated_at`)
    .bind(record.date, record.content, now) : db.prepare('DELETE FROM calendar_memos WHERE date = ?').bind(record.date);
  // Amounts and the day's note must either both save or neither save.
  await db.batch([money, memo]);
}
