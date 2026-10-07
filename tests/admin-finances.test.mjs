import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { listDayFinances, parseDayFinance, saveDayFinance, validFinanceMonth } from '../lib/admin-finances.ts';
import { listDayMemos, saveDayMemo } from '../lib/admin-planner.ts';

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
const record = { date: '2026-10-08', paidAmount: 12000, receivedAmount: 23000, content: '판매 완료' };
test('daily won amounts reject decimals, negatives, unsafe values and invalid dates', () => {
  assert.deepEqual(parseDayFinance(record), record);
  assert.equal(parseDayFinance({ ...record, paidAmount: 0 }).paidAmount, 0);
  for (const paidAmount of [-1, 1.5, '1000', null, Infinity, NaN, 1e12, Number.MAX_SAFE_INTEGER])
    assert.equal(parseDayFinance({ ...record, paidAmount }), null);
  for (const receivedAmount of [-1, '1000', 1.5, 1e12]) assert.equal(parseDayFinance({ ...record, receivedAmount }), null);
  assert.equal(parseDayFinance({ ...record, date: '2026-02-30' }), null);
  assert.equal(parseDayFinance({ ...record, content: 'x'.repeat(2001) }), null);
  assert.equal(parseDayFinance({ ...record, content: '\u0000' }), null);
  for (const month of ['2026-10', '2028-02', '2100-12']) assert.equal(validFinanceMonth(month), true);
  for (const month of ['', '2026-13', '2026-1', '2026-10-01', '1999-01']) assert.equal(validFinanceMonth(month), false);
});
test('daily saves update once per date, preserve other months and existing memo-only clients', async () => {
  const { db, close } = database();
  try {
    await saveDayFinance(db, record);
    await saveDayFinance(db, { ...record, date: '2026-10-09', paidAmount: 9000, receivedAmount: 0 });
    await saveDayFinance(db, { ...record, date: '2026-11-01', paidAmount: 99000 });
    await saveDayFinance(db, { ...record, receivedAmount: 33000, content: '수정' });
    let rows = await listDayFinances(db, '2026-10');
    assert.equal(rows.length, 2);
    assert.equal(rows.reduce((sum, row) => sum + row.paidAmount, 0), 21000);
    assert.equal(rows.reduce((sum, row) => sum + row.receivedAmount, 0), 33000);
    assert.equal((await listDayFinances(db, '2026-11')).length, 1);
    await saveDayMemo(db, record.date, '구버전에서 메모 변경');
    assert.equal((await listDayFinances(db, '2026-10'))[0].receivedAmount, 33000);
    await saveDayFinance(db, { ...record, paidAmount: 0, receivedAmount: 0, content: '' });
    rows = await listDayFinances(db, '2026-10');
    assert.equal(rows[0].paidAmount, 0); assert.equal(rows[0].receivedAmount, 0);
    assert.equal((await listDayMemos(db)).some(row => row.date === record.date), false);
  } finally { close(); }
});
test('a failed daily memo write rolls back the associated money update', async () => {
  const { db, sql, close } = database();
  try {
    await saveDayFinance(db, record);
    sql.exec(`CREATE TRIGGER reject_test_note BEFORE INSERT ON calendar_memos
      WHEN NEW.content = '실패' BEGIN SELECT RAISE(ABORT, 'test failure'); END`);
    await assert.rejects(saveDayFinance(db, { ...record, receivedAmount: 99999, content: '실패' }));
    assert.equal((await listDayFinances(db, '2026-10'))[0].receivedAmount, 23000);
    assert.equal((await listDayMemos(db))[0].content, record.content);
  } finally { close(); }
});
