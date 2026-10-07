export type PickupRecord = {
  id: string;
  created_at: number;
  name: string;
  phone: string;
  address: string;
  amount: string;
  date: string;
  timeSlot: string;
  pickupMethod: string;
  message: string;
  status: 'new' | 'contacted' | 'done';
};

export type NewPickup = Pick<PickupRecord, 'name' | 'phone' | 'address' | 'amount' | 'date' | 'timeSlot' | 'pickupMethod' | 'message'>;

export type CustomerMemo = {
  id: string; phone: string; name: string; title: string; content: string;
  created_at: number; updated_at: number;
};
export type MemoInput = Pick<CustomerMemo, 'phone' | 'name' | 'title' | 'content'>;

export function adminReady(env: NodeJS.ProcessEnv, db?: D1Database): boolean {
  return !!db && !!env.ADMIN_PASSWORD && env.ADMIN_PASSWORD.length >= 16 &&
    !!env.ADMIN_SESSION_SECRET && env.ADMIN_SESSION_SECRET.length >= 32;
}

export async function ensureTables(db: D1Database) {
  await db.prepare(`CREATE TABLE IF NOT EXISTS pickup_requests (
    id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, name TEXT NOT NULL,
    phone TEXT NOT NULL, address TEXT NOT NULL, amount TEXT NOT NULL,
    date TEXT NOT NULL, time_slot TEXT NOT NULL DEFAULT '미기재',
    pickup_method TEXT NOT NULL DEFAULT '미기재',
    message TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'new',
    last_activity_at INTEGER NOT NULL DEFAULT 0
  )`).run();
  const columns = await db.prepare('PRAGMA table_info(pickup_requests)').all<{ name: string }>();
  for (const column of ['time_slot', 'pickup_method', 'last_activity_at']) {
    if (columns.results.some(item => item.name === column)) continue;
    try {
      await db.prepare(column === 'last_activity_at'
        ? 'ALTER TABLE pickup_requests ADD COLUMN last_activity_at INTEGER NOT NULL DEFAULT 0'
        : `ALTER TABLE pickup_requests ADD COLUMN ${column} TEXT NOT NULL DEFAULT '미기재'`).run();
    } catch (error) {
      // Another request may have added the column first; verify before continuing.
      const current = await db.prepare('PRAGMA table_info(pickup_requests)').all<{ name: string }>();
      if (!current.results.some(item => item.name === column)) throw error;
    }
  }
  await db.prepare(`CREATE TABLE IF NOT EXISTS admin_devices (
    token TEXT PRIMARY KEY, created_at INTEGER NOT NULL
  )`).run();
  await db.prepare(`CREATE TABLE IF NOT EXISTS admin_login_attempts (
    ip TEXT PRIMARY KEY, attempts INTEGER NOT NULL, reset_at INTEGER NOT NULL
  )`).run();
  await db.prepare(`CREATE TABLE IF NOT EXISTS customer_memos (
    id TEXT PRIMARY KEY, phone TEXT NOT NULL, name TEXT NOT NULL,
    title TEXT NOT NULL, content TEXT NOT NULL,
    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
  )`).run();
  await db.prepare('CREATE INDEX IF NOT EXISTS customer_memos_phone ON customer_memos(phone)').run();
  await db.prepare(`CREATE TABLE IF NOT EXISTS calendar_memos (
    date TEXT PRIMARY KEY, content TEXT NOT NULL, updated_at INTEGER NOT NULL
  )`).run();
  await db.prepare('UPDATE pickup_requests SET last_activity_at = created_at WHERE last_activity_at = 0').run();
  const cutoffDate = new Date();
  cutoffDate.setUTCFullYear(cutoffDate.getUTCFullYear() - 10);
  const cutoff = cutoffDate.getTime();
  await db.prepare('DELETE FROM calendar_memos WHERE updated_at < ?').bind(cutoff).run();
  await db.prepare(`DELETE FROM customer_memos WHERE updated_at < ? AND phone NOT IN
    (SELECT phone FROM pickup_requests WHERE last_activity_at >= ?)`).bind(cutoff, cutoff).run();
  await db.prepare(`DELETE FROM pickup_requests WHERE last_activity_at < ? AND phone NOT IN
    (SELECT phone FROM customer_memos WHERE updated_at >= ?)`).bind(cutoff, cutoff).run();
}

export async function savePickup(db: D1Database, pickup: NewPickup, requestId?: string) {
  await ensureTables(db);
  const id = requestId || crypto.randomUUID();
  await db.prepare(`INSERT INTO pickup_requests
    (id, created_at, name, phone, address, amount, date, time_slot, pickup_method, message, status, last_activity_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'new', ?) ON CONFLICT(id) DO NOTHING`)
    .bind(id, Date.now(), pickup.name, pickup.phone, pickup.address, pickup.amount, pickup.date,
      pickup.timeSlot, pickup.pickupMethod, pickup.message, Date.now()).run();
  return id;
}

export async function listPickups(db: D1Database) {
  await ensureTables(db);
  const result = await db.prepare(`SELECT id, created_at, name, phone, address, amount, date,
    time_slot AS timeSlot, pickup_method AS pickupMethod, message, status
    FROM pickup_requests ORDER BY created_at DESC`).all<PickupRecord>();
  return result.results;
}

export async function setPickupStatus(db: D1Database, id: string, status: PickupRecord['status']) {
  await ensureTables(db);
  const result = await db.prepare('UPDATE pickup_requests SET status = ?, last_activity_at = ? WHERE id = ?')
    .bind(status, Date.now(), id).run();
  return (result.meta.changes ?? 0) > 0;
}

export async function deletePickup(db: D1Database, id: string) {
  await ensureTables(db);
  const result = await db.prepare('DELETE FROM pickup_requests WHERE id = ?').bind(id).run();
  return (result.meta.changes ?? 0) > 0;
}

export async function listMemos(db: D1Database) {
  await ensureTables(db);
  const result = await db.prepare(`SELECT id, phone, name, title, content, created_at, updated_at
    FROM customer_memos ORDER BY updated_at DESC`).all<CustomerMemo>();
  return result.results;
}

export async function saveMemo(db: D1Database, input: MemoInput, id?: string) {
  await ensureTables(db);
  const now = Date.now();
  if (id) {
    const result = await db.prepare(`UPDATE customer_memos SET phone = ?, name = ?, title = ?, content = ?, updated_at = ? WHERE id = ?`)
      .bind(input.phone, input.name, input.title, input.content, now, id).run();
    return (result.meta.changes ?? 0) > 0 ? id : null;
  }
  const newId = crypto.randomUUID();
  await db.prepare(`INSERT INTO customer_memos (id, phone, name, title, content, created_at, updated_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)`).bind(newId, input.phone, input.name, input.title, input.content, now, now).run();
  return newId;
}

export async function deleteMemo(db: D1Database, id: string) {
  await ensureTables(db);
  const result = await db.prepare('DELETE FROM customer_memos WHERE id = ?').bind(id).run();
  return (result.meta.changes ?? 0) > 0;
}

const encoder = new TextEncoder();

function b64url(bytes: Uint8Array) {
  let raw = '';
  for (const byte of bytes) raw += String.fromCharCode(byte);
  return btoa(raw).replaceAll('+', '-').replaceAll('/', '_').replaceAll('=', '');
}

function fromB64url(value: string) {
  const raw = atob(value.replaceAll('-', '+').replaceAll('_', '/'));
  return Uint8Array.from(raw, char => char.charCodeAt(0));
}

async function hmac(secret: string, value: string) {
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return b64url(new Uint8Array(await crypto.subtle.sign('HMAC', key, encoder.encode(value))));
}

export async function makeSession(secret: string) {
  const expires = Date.now() + 30 * 86400000;
  const nonce = crypto.randomUUID();
  const body = `${expires}.${nonce}`;
  return `${body}.${await hmac(secret, body)}`;
}

export async function validAdminToken(token: string | undefined, secret?: string) {
  if (!secret) return false;
  if (!token) return false;
  const parts = token.split('.');
  if (parts.length !== 3 || !/^\d+$/.test(parts[0]) || Number(parts[0]) < Date.now()) return false;
  const expected = await hmac(secret, `${parts[0]}.${parts[1]}`);
  const given = parts[2];
  if (expected.length !== given.length) return false;
  let different = 0;
  for (let i = 0; i < expected.length; i++) different |= expected.charCodeAt(i) ^ given.charCodeAt(i);
  return different === 0;
}

export async function validBearer(request: Request, secret?: string) {
  const header = request.headers.get('authorization');
  return validAdminToken(header?.startsWith('Bearer ') ? header.slice(7) : undefined, secret);
}

export function sameOrigin(request: Request) {
  const origin = request.headers.get('origin');
  return origin === new URL(request.url).origin;
}

export function privateJson(value: unknown, status = 200) {
  return Response.json(value, { status, headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
}

export async function passwordMatches(input: string, expected: string) {
  const [a, b] = await Promise.all([crypto.subtle.digest('SHA-256', encoder.encode(input)), crypto.subtle.digest('SHA-256', encoder.encode(expected))]);
  const left = new Uint8Array(a), right = new Uint8Array(b);
  let different = 0;
  for (let i = 0; i < left.length; i++) different |= left[i] ^ right[i];
  return different === 0;
}

export async function loginAllowed(db: D1Database, ip: string) {
  await ensureTables(db);
  const row = await db.prepare('SELECT attempts, reset_at FROM admin_login_attempts WHERE ip = ?').bind(ip).first<{ attempts: number; reset_at: number }>();
  return !row || row.reset_at < Date.now() || row.attempts < 5;
}

export async function recordLoginFailure(db: D1Database, ip: string) {
  const now = Date.now();
  await db.prepare(`INSERT INTO admin_login_attempts (ip, attempts, reset_at) VALUES (?, 1, ?)
    ON CONFLICT(ip) DO UPDATE SET attempts = CASE WHEN reset_at < ? THEN 1 ELSE attempts + 1 END,
    reset_at = CASE WHEN reset_at < ? THEN excluded.reset_at ELSE reset_at END`)
    .bind(ip, now + 15 * 60000, now, now).run();
}

export async function clearLoginFailures(db: D1Database, ip: string) {
  await db.prepare('DELETE FROM admin_login_attempts WHERE ip = ?').bind(ip).run();
}

export async function addDeviceToken(db: D1Database, token: string) {
  await ensureTables(db);
  await db.prepare(`INSERT INTO admin_devices (token, created_at) VALUES (?, ?)
    ON CONFLICT(token) DO UPDATE SET created_at = excluded.created_at`)
    .bind(token, Date.now()).run();
}

export async function removeDeviceToken(db: D1Database, token: string) {
  await db.prepare('DELETE FROM admin_devices WHERE token = ?').bind(token).run();
}

type ServiceAccount = { project_id: string; client_email: string; private_key: string };
let cachedAccessToken: { value: string; expires: number } | null = null;

function serviceAccount(env: NodeJS.ProcessEnv): ServiceAccount | null {
  if (!env.FCM_SERVICE_ACCOUNT_JSON && !env.FCM_SERVICE_ACCOUNT_JSON_BASE64) return null;
  try {
    const json = env.FCM_SERVICE_ACCOUNT_JSON ||
      new TextDecoder().decode(fromB64url(env.FCM_SERVICE_ACCOUNT_JSON_BASE64!));
    const parsed = JSON.parse(json) as ServiceAccount;
    if (!parsed.project_id || !parsed.client_email || !parsed.private_key) return null;
    return parsed;
  } catch { return null; }
}

async function accessToken(account: ServiceAccount, send: typeof fetch) {
  if (cachedAccessToken && cachedAccessToken.expires > Date.now() + 60000) return cachedAccessToken.value;
  const pem = account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  const raw = Uint8Array.from(atob(pem), char => char.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', raw, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const now = Math.floor(Date.now() / 1000);
  const header = b64url(encoder.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const claims = b64url(encoder.encode(JSON.stringify({ iss: account.client_email,
    scope: 'https://www.googleapis.com/auth/firebase.messaging', aud: 'https://oauth2.googleapis.com/token',
    iat: now, exp: now + 3600 })));
  const body = `${header}.${claims}`;
  const signature = b64url(new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(body))));
  const response = await send('https://oauth2.googleapis.com/token', { method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${body}.${signature}` }),
    signal: AbortSignal.timeout(8000) });
  if (!response.ok) throw new Error('FCM OAuth failed');
  const result = await response.json() as { access_token?: string; expires_in?: number };
  if (!result.access_token) throw new Error('FCM OAuth missing access token');
  cachedAccessToken = { value: result.access_token, expires: Date.now() + (result.expires_in || 3600) * 1000 };
  return result.access_token;
}

export async function notifyAdmins(db: D1Database, env: NodeJS.ProcessEnv, send: typeof fetch = fetch) {
  const account = serviceAccount(env);
  if (!account) return { devices: 0, accepted: 0 };
  const { results } = await db.prepare('SELECT token FROM admin_devices ORDER BY created_at DESC LIMIT 10').all<{ token: string }>();
  if (!results.length) return { devices: 0, accepted: 0 };
  const bearer = await accessToken(account, send);
  const sent = await Promise.allSettled(results.map(async ({ token }) => {
    try {
      const response = await send(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`, {
        method: 'POST', headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ message: { token, data: { type: 'pickup_request' }, android: { priority: 'high' } } }),
        signal: AbortSignal.timeout(4000),
      });
      if (response.status === 404) await removeDeviceToken(db, token);
      return response.ok;
    } catch { return false; }
  }));
  return { devices: results.length, accepted: sent.filter(item => item.status === 'fulfilled' && item.value).length };
}
