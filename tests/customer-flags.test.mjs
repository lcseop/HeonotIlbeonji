import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { parseCustomerFlag, saveCustomerFlag, savePickup, listPickups, deletePickup, getCustomerFlag } from '../lib/admin.ts';

function database() {
  const sql = new DatabaseSync(':memory:');
  return { sql, db: { prepare(query) {
    const stmt = sql.prepare(query);
    const bound = args => ({ run: async () => ({ meta: { changes: Number(stmt.run(...args).changes) } }),
      all: async () => ({ results: stmt.all(...args) }), first: async () => stmt.get(...args) || null });
    return { ...bound([]), bind: (...args) => bound(args) };
  } } };
}
const pickup = { name:'고객', phone:'01012345678', address:'주소', amount:'20kg', date:'2026-10-09', timeSlot:'오전', pickupMethod:'대면 수거', message:'' };

test('flags use normalized phone numbers and reject invalid values', () => {
  assert.deepEqual(parseCustomerFlag({phone:'+82 10-1234-5678',flag:'regular'}),{phone:'01012345678',flag:'regular'});
  for (const data of [{phone:'abc',flag:'regular'},{phone:pickup.phone,flag:'unknown'},{phone:pickup.phone,flag:{}},{phone:123,flag:''}]) assert.equal(parseCustomerFlag(data),null);
});
test('a phone flag applies to existing and future requests, survives request deletion and can be cleared', async () => {
  const {sql,db}=database();
  try {
    const first=crypto.randomUUID(), second=crypto.randomUUID(), other=crypto.randomUUID();
    await savePickup(db,pickup,first);
    await saveCustomerFlag(db,pickup.phone,'regular');
    await savePickup(db,pickup,second); await savePickup(db,{...pickup,phone:'01099999999'},other);
    let rows=await listPickups(db);
    assert.equal(rows.find(r=>r.id===first).customerFlag,'regular');
    assert.equal(rows.find(r=>r.id===second).customerFlag,'regular');
    assert.equal(rows.find(r=>r.id===other).customerFlag,'');
    await saveCustomerFlag(db,pickup.phone,'blacklist');
    assert.equal(await getCustomerFlag(db,pickup.phone),'blacklist');
    await deletePickup(db,first); await deletePickup(db,second);
    const third=crypto.randomUUID(); await savePickup(db,pickup,third);
    assert.equal((await listPickups(db)).find(r=>r.id===third).customerFlag,'blacklist');
    await saveCustomerFlag(db,pickup.phone,'');
    assert.equal((await listPickups(db)).find(r=>r.id===third).customerFlag,'');
  } finally {sql.close();}
});
test('legacy formatted and international numbers match the same flag', async () => {
  const {sql,db}=database();
  try {
    await saveCustomerFlag(db,pickup.phone,'regular');
    await savePickup(db,{...pickup,phone:'+82 10-1234-5678'},crypto.randomUUID());
    await savePickup(db,{...pickup,phone:'010-1234-5678'},crypto.randomUUID());
    assert.ok((await listPickups(db)).every(row=>row.customerFlag==='regular'));
  } finally {sql.close();}
});
