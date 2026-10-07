import test from 'node:test';
import assert from 'node:assert/strict';
import { dashboardConfiguration, submitDashboardPickup } from '../lib/dashboard-pickup.ts';
import { deleteMemo, deletePickup, ensureTables, makeSession, saveMemo, validAdminToken } from '../lib/admin.ts';
import { notifyAdmins } from '../lib/admin.ts';
import { generateKeyPairSync, createVerify } from 'node:crypto';

const env = {
  SITE_ORIGIN: 'https://pickup.example', TURNSTILE_SITE_KEY: 'test-site', TURNSTILE_SECRET_KEY: 'test-secret',
  ADMIN_PASSWORD: 'long-test-password-only', ADMIN_SESSION_SECRET: 'long-session-secret-for-tests-only-12345',
  PICKUP_DELIVERY_MODE: 'dashboard',
};
const now = new Date('2026-09-29T15:30:00Z');
const payload = { name: '테스트', phone: '010-1234-5678', address: '테스트 주소', amount: '20~30kg', date: '2026-10-01', timeSlot: '오후', pickupMethod: '비대면 수거', message: '테스트', consent: true, token: 'test-token' };
function request(data = payload, origin = env.SITE_ORIGIN) {
  return new Request(`${env.SITE_ORIGIN}/api/pickup`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
}
function database() {
  const inserted = [];
  return {
    inserted,
    prepare(sql) {
      return {
        bind(...args) {
          return { run: async () => {
            if (sql.includes('INSERT INTO pickup_requests')) inserted.push(args);
            return { meta: { changes: 1 } };
          } };
        },
        run: async () => ({ meta: { changes: 0 } }),
        all: async () => ({ results: [] }),
      };
    },
  };
}

test('dashboard mode enables the form without Solapi credentials', async () => {
  const db = database();
  assert.deepEqual(await dashboardConfiguration(env, db).json(), { enabled: true, siteKey: 'test-site', delivery: 'dashboard' });
  assert.equal((await dashboardConfiguration(env).json()).enabled, false);
});

test('existing pickup records gain the new columns without clearing old rows', async () => {
  const columns = new Set(['id', 'created_at', 'name', 'phone', 'address', 'amount', 'date', 'message', 'status']);
  const changed = [];
  const db = { prepare(sql) { return {
    bind: () => ({ run: async () => ({ meta: { changes: 0 } }) }),
    all: async () => ({ results: [...columns].map(name => ({ name })) }),
    run: async () => {
      const match = sql.match(/^ALTER TABLE pickup_requests ADD COLUMN (\w+)/);
      if (match) { columns.add(match[1]); changed.push(match[1]); }
      return { meta: { changes: 0 } };
    },
  }; } };
  await ensureTables(db);
  await ensureTables(db);
  assert.deepEqual(changed, ['time_slot', 'pickup_method', 'last_activity_at', 'reserved_time', 'admin_note', 'admin_note_updated_at']);
  assert.ok(columns.has('id'));
});

test('verified request is saved once without calling Solapi', async () => {
  const db = database();
  const urls = [];
  const send = async url => {
    urls.push(url);
    assert.match(url, /siteverify$/);
    return Response.json({ success: true, hostname: 'pickup.example', action: 'pickup-request' });
  };
  const response = await submitDashboardPickup(request(), env, db, send, now);
  assert.equal(response.status, 200);
  assert.equal((await response.json()).accepted, true);
  assert.equal(db.inserted.length, 1);
  assert.equal(db.inserted[0][3], '01012345678');
  assert.equal(db.inserted[0][7], '오후');
  assert.equal(db.inserted[0][8], '비대면 수거');
  assert.equal(urls.length, 1);
});

test('memo and application deletion stay scoped to the selected IDs', async () => {
  const statements = [];
  const db = { prepare(sql) { return {
    all: async () => ({ results: [] }),
    run: async () => ({ meta: { changes: 0 } }),
    bind(...args) { statements.push({ sql, args }); return { run: async () => ({ meta: { changes: 1 } }) }; },
  }; } };
  const memoId = await saveMemo(db, { phone: '01012345678', name: '고객', title: '재방문', content: '오후 연락' });
  assert.match(memoId, /^[0-9a-f-]{36}$/);
  assert.equal(await deleteMemo(db, memoId), true);
  assert.equal(await deletePickup(db, 'test-request-id'), true);
  assert.ok(statements.some(item => item.sql === 'DELETE FROM customer_memos WHERE id = ?' && item.args[0] === memoId));
  assert.ok(statements.some(item => item.sql === 'DELETE FROM pickup_requests WHERE id = ?' && item.args[0] === 'test-request-id'));
});

test('invalid origin or challenge never stores an application', async () => {
  const db = database();
  assert.equal((await submitDashboardPickup(request(payload, 'https://other.example'), env, db, () => assert.fail('must not call'), now)).status, 403);
  assert.equal((await submitDashboardPickup(request(), env, db, async () => Response.json({ success: false }), now)).status, 400);
  assert.equal(db.inserted.length, 0);
});

test('time slot and pickup method must be selected from the offered choices', async () => {
  const db = database();
  const verify = async () => Response.json({ success: true, hostname: 'pickup.example', action: 'pickup-request' });
  assert.equal((await submitDashboardPickup(request({ ...payload, timeSlot: '' }), env, db, verify, now)).status, 400);
  assert.equal((await submitDashboardPickup(request({ ...payload, pickupMethod: '우편' }), env, db, verify, now)).status, 400);
  assert.equal(db.inserted.length, 0);
});

test('admin session requires a valid server signature', async () => {
  const token = await makeSession(env.ADMIN_SESSION_SECRET);
  assert.equal(await validAdminToken(token, env.ADMIN_SESSION_SECRET), true);
  assert.equal(await validAdminToken(token, 'different-secret'), false);
});

test('FCM sends a private-data-free alert to a registered Android device', async () => {
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const account = { project_id: 'test-project', client_email: 'admin@test-project.iam.gserviceaccount.com',
    private_key: privateKey.export({ type: 'pkcs8', format: 'pem' }) };
  const db = { prepare: () => ({ all: async () => ({ results: [{ token: 'fcm-test-device-token' }] }) }) };
  let calls = 0;
  const result = await notifyAdmins(db, { FCM_SERVICE_ACCOUNT_JSON: JSON.stringify(account) }, async (url, options) => {
    calls++;
    if (url.includes('oauth2.googleapis.com')) {
      const assertion = new URLSearchParams(options.body).get('assertion');
      const [header, payload, signature] = assertion.split('.');
      const verify = createVerify('RSA-SHA256');
      verify.update(`${header}.${payload}`);
      assert.equal(verify.verify(publicKey, Buffer.from(signature, 'base64url')), true);
      return Response.json({ access_token: 'test-access-token', expires_in: 3600 });
    }
    assert.equal(url, 'https://fcm.googleapis.com/v1/projects/test-project/messages:send');
    assert.equal(options.headers.Authorization, 'Bearer test-access-token');
    const body = JSON.parse(options.body);
    assert.deepEqual(body.message.data, { type: 'pickup_request' });
    assert.equal(body.message.token, 'fcm-test-device-token');
    assert.ok(!options.body.includes('010'));
    return Response.json({ name: 'test-message' });
  });
  assert.equal(calls, 2);
  assert.deepEqual(result, { devices: 1, accepted: 1 });
});
