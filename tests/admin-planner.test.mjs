import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { parseManualPickup, validPlannerDate, listDayMemos, saveDayMemo, editManualPickup, parseAdminDetails, saveAdminDetails } from '../lib/admin-planner.ts';
import { savePickup, listPickups, setPickupStatus, deletePickup } from '../lib/admin.ts';

const input = { name: '전화 고객', phone: '+82 10-1234-5678', address: '', amount: '수거량 확인 필요',
  date: '', timeSlot: '시간 협의', pickupMethod: '방식 협의', message: '방문 상담' };
function database() {
  const sql = new DatabaseSync(':memory:');
  const db = { prepare(query) {
    const statement = sql.prepare(query);
    const bound = args => ({
      run: async () => ({ meta: { changes: Number(statement.run(...args).changes) } }),
      all: async () => ({ results: statement.all(...args) }),
    });
    return { ...bound([]), bind: (...args) => bound(args) };
  } };
  return { db, close: () => sql.close() };
}
test('manual consultation supports imported contacts, undecided dates and landlines', () => {
  assert.equal(parseManualPickup(input).phone, '01012345678');
  assert.equal(parseManualPickup({ ...input, phone: '02-1234-5678' }).phone, '0212345678');
  assert.equal(parseManualPickup({ ...input, date: '2026-10-07' }).date, '2026-10-07');
  for (const change of [{ phone: 'abc' }, { name: '' }, { date: '2026-02-30' },
    { timeSlot: '아무 시간' }, { pickupMethod: 'unknown' }, { message: 'x'.repeat(351) }])
    assert.equal(parseManualPickup({ ...input, ...change }), null);
});
test('planner dates reject overflow and support leap years', () => {
  assert.equal(validPlannerDate('2028-02-29'), true);
  for (const date of ['2026-02-29', '2026-13-01', '26-10-01', '', '1900-01-01']) assert.equal(validPlannerDate(date), false);
});
test('manual save is idempotent and editing keeps the processing status', async () => {
  const { db, close } = database();
  try {
    const id = crypto.randomUUID(), pickup = parseManualPickup(input);
    await savePickup(db, pickup, id); await savePickup(db, pickup, id);
    assert.equal((await listPickups(db)).length, 1);
    await setPickupStatus(db, id, 'contacted');
    assert.equal(await editManualPickup(db, id, { ...pickup, date: '2026-10-08', address: '확인한 주소' }), true);
    const [saved] = await listPickups(db);
    assert.equal(saved.status, 'contacted'); assert.equal(saved.date, '2026-10-08');
    assert.equal(saved.address, '확인한 주소');
    assert.equal(await editManualPickup(db, crypto.randomUUID(), pickup), false);
  } finally { close(); }
});
test('date memos persist per day, update in place and only delete the chosen day', async () => {
  const { db, close } = database();
  try {
    await saveDayMemo(db, '2026-10-07', '오전 휴무');
    await saveDayMemo(db, '2026-10-08', '김포 동선');
    await saveDayMemo(db, '2026-10-07', '오후만 방문');
    let notes = await listDayMemos(db);
    assert.equal(notes.length, 2); assert.equal(notes.find(n => n.date === '2026-10-07').content, '오후만 방문');
    await saveDayMemo(db, '2026-10-07', '');
    notes = await listDayMemos(db); assert.equal(notes.length, 1); assert.equal(notes[0].date, '2026-10-08');
  } finally { close(); }
});

test('admin additions stay on the chosen request, leaving customer submissions and same-phone requests intact', async () => {
  const { db, close } = database();
  try {
    const first = crypto.randomUUID(), second = crypto.randomUUID(), pickup = parseManualPickup(input);
    await savePickup(db, pickup, first); await savePickup(db, pickup, second);
    await setPickupStatus(db, first, 'contacted');
    assert.equal(await saveAdminDetails(db, first, { reservedTime: '09:05', adminNote: '문 앞에서 전화' }), true);
    const rows = await listPickups(db), saved = rows.find(r => r.id === first), other = rows.find(r => r.id === second);
    assert.equal(saved.reservedTime, '09:05'); assert.equal(saved.adminNote, '문 앞에서 전화');
    assert.equal(saved.message, pickup.message); assert.equal(saved.timeSlot, pickup.timeSlot);
    assert.equal(saved.status, 'contacted'); assert.ok(saved.adminNoteUpdatedAt > 0);
    assert.equal(other.adminNote, ''); assert.equal(other.reservedTime, '');
    await editManualPickup(db, first, { ...pickup, address: '방문 주소' });
    assert.equal((await listPickups(db)).find(r => r.id === first).reservedTime, '09:05');
    await saveAdminDetails(db, first, { reservedTime: '', adminNote: '' });
    assert.equal((await listPickups(db)).find(r => r.id === first).adminNote, '');
    assert.equal(await saveAdminDetails(db, crypto.randomUUID(), { reservedTime: '', adminNote: '없는 신청서' }), false);
    await deletePickup(db, first); assert.equal((await listPickups(db)).length, 1);
  } finally { close(); }
});

test('reservation times accept only hour and minute, with bounded note content', () => {
  for (const time of ['', '00:00', '09:05', '23:59']) assert.ok(parseAdminDetails({ reservedTime: time, adminNote: '기록' }));
  for (const time of ['24:00', '10:60', '9:05', '09:05:00', null]) assert.equal(parseAdminDetails({ reservedTime: time, adminNote: '' }), null);
  assert.equal(parseAdminDetails({ reservedTime: '', adminNote: 'x'.repeat(2001) }), null);
  assert.equal(parseAdminDetails({ reservedTime: '', adminNote: '\u0000' }), null);
});
