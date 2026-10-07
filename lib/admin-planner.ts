import { ensureTables, type NewPickup } from './admin.ts';

export function validPlannerDate(value: unknown): value is string {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value &&
    value >= '2000-01-01' && value <= '2100-12-31';
}

export function parseAdminDetails(data: Record<string, unknown>) {
  if (typeof data.reservedTime !== 'string' || typeof data.adminNote !== 'string' ||
    (data.reservedTime !== '' && !/^([01]\d|2[0-3]):[0-5]\d$/.test(data.reservedTime)) ||
    data.adminNote.length > 2000 || /[\u0000-\u0008\u000b-\u001f\u007f]/.test(data.adminNote)) return null;
  return { reservedTime: data.reservedTime, adminNote: data.adminNote.trim() };
}

export async function saveAdminDetails(db: D1Database, id: string, details: { reservedTime: string; adminNote: string }) {
  await ensureTables(db);
  const now = Date.now();
  const result = await db.prepare(`UPDATE pickup_requests SET reserved_time = ?, admin_note = ?,
    admin_note_updated_at = ?, last_activity_at = ? WHERE id = ?`)
    .bind(details.reservedTime, details.adminNote, now, now, id).run();
  return (result.meta.changes ?? 0) > 0;
}

// Phone consultations may be logged before an address or visit date is agreed.
export function parseManualPickup(data: Record<string, unknown>): NewPickup | null {
  const limits = { name: 40, phone: 30, address: 150, amount: 40, date: 10, timeSlot: 15, pickupMethod: 15, message: 350 };
  const fields: Record<string, string> = {};
  for (const [key, limit] of Object.entries(limits)) {
    if (typeof data[key] !== 'string' || (data[key] as string).length > limit ||
      /[\u0000-\u0008\u000b-\u001f\u007f]/.test(data[key] as string)) return null;
    fields[key] = (data[key] as string).trim();
    if (key !== 'message' && /[\r\n]/.test(fields[key])) return null;
  }
  fields.phone = fields.phone.replace(/[\s()-]/g, '').replace(/^\+82/, '0');
  if (!fields.name || !/^0\d{8,10}$/.test(fields.phone) ||
    (fields.date && !validPlannerDate(fields.date)) ||
    !['시간 협의', '오전', '오후'].includes(fields.timeSlot) ||
    !['방식 협의', '대면 수거', '비대면 수거'].includes(fields.pickupMethod)) return null;
  return fields as NewPickup;
}

export async function listDayMemos(db: D1Database) {
  await ensureTables(db);
  return (await db.prepare('SELECT date, content, updated_at FROM calendar_memos ORDER BY date DESC')
    .all<{ date: string; content: string; updated_at: number }>()).results;
}

export async function editManualPickup(db: D1Database, id: string, pickup: NewPickup) {
  await ensureTables(db);
  const result = await db.prepare(`UPDATE pickup_requests SET name = ?, phone = ?, address = ?,
    amount = ?, date = ?, time_slot = ?, pickup_method = ?, message = ?, last_activity_at = ? WHERE id = ?`)
    .bind(pickup.name, pickup.phone, pickup.address, pickup.amount, pickup.date, pickup.timeSlot,
      pickup.pickupMethod, pickup.message, Date.now(), id).run();
  return (result.meta.changes ?? 0) > 0;
}

export async function saveDayMemo(db: D1Database, date: string, content: string) {
  await ensureTables(db);
  if (!content) {
    await db.prepare('DELETE FROM calendar_memos WHERE date = ?').bind(date).run();
  } else {
    await db.prepare(`INSERT INTO calendar_memos (date, content, updated_at) VALUES (?, ?, ?)
      ON CONFLICT(date) DO UPDATE SET content = excluded.content, updated_at = excluded.updated_at`)
      .bind(date, content, Date.now()).run();
  }
}
