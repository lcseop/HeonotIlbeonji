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
    message TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'new'
  )`).run();
  const columns = await db.prepare('PRAGMA table_info(pickup_requests)').all<{ name: string }>();
  for (const column of ['time_slot', 'pickup_method']) {
    if (columns.results.some(item => item.name === column)) continue;
    try {
      await db.prepare(`ALTER TABLE pickup_requests ADD COLUMN ${column} TEXT NOT NULL DEFAULT '미기재'`).run();
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
}

export async function savePickup(db: D1Database, pickup: NewPickup) {
  await ensureTables(db);
  const id = crypto.randomUUID();
  await db.prepare(`INSERT INTO pickup_requests
    (id, created_at, name, phone, address, amount, date, time_slot, pickup_method, message, status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'new')`)
    .bind(id, Date.now(), pickup.name, pickup.phone, pickup.address, pickup.amount, pickup.date,
      pickup.timeSlot, pickup.pickupMethod, pickup.message).run();
  return id;
}

export async function listPickups(db: D1Database) {
  await ensureTables(db);
  const result = await db.prepare(`SELECT id, created_at, name, phone, address, amount, date,
    time_slot AS timeSlot, pickup_method AS pickupMethod, message, status
    FROM pickup_requests ORDER BY created_at DESC LIMIT 200`).all<PickupRecord>();
  return result.results;
}

export async function setPickupStatus(db: D1Database, id: string, status: PickupRecord['status']) {
  await ensureTables(db);
  const result = await db.prepare('UPDATE pickup_requests SET status = ? WHERE id = ?').bind(status, id).run();
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
