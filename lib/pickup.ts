type Environment = Record<string, string | undefined>;
type Pickup = { name: string; phone: string; address: string; amount: string; date: string; timeSlot: string; pickupMethod: string; message: string };
const amounts = new Set(['20~30kg', '30kg 이상', '기타 품목 상담', '수거량 확인 필요']);
const timeSlots = new Set(['오전', '오후', '시간 협의']);
const pickupMethods = new Set(['대면 수거', '비대면 수거']);
const unavailable = '현재 온라인 접수가 어렵습니다. 010-4880-8259로 전화해 주세요.';
const uncertain = '문자 접수 결과를 확인하지 못했습니다. 중복 신청하지 마시고 010-4880-8259로 접수 여부를 확인해 주세요.';

function reply(status: number, data: Record<string, unknown>) {
  return Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
}

function settings(env: Environment) {
  const values = ['SOLAPI_API_KEY', 'SOLAPI_API_SECRET', 'SOLAPI_FROM', 'TURNSTILE_SITE_KEY', 'TURNSTILE_SECRET_KEY', 'SITE_ORIGIN'];
  if (env.PICKUP_SMS_ENABLED !== 'true' || values.some(key => !env[key]?.trim())) return null;
  try {
    const origin = new URL(env.SITE_ORIGIN!);
    if (origin.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(origin.hostname)) return null;
    const from = env.SOLAPI_FROM!.replace(/[ -]/g, '');
    const to = (env.PICKUP_SMS_TO || '01048808259').replace(/[ -]/g, '');
    if (!/^0\d{8,10}$/.test(from) || !/^01[016789]\d{7,8}$/.test(to)) return null;
    return { origin, from, to, apiKey: env.SOLAPI_API_KEY!, secret: env.SOLAPI_API_SECRET!, siteKey: env.TURNSTILE_SITE_KEY!, captchaSecret: env.TURNSTILE_SECRET_KEY! };
  } catch { return null; }
}

export function validatePickup(data: Record<string, unknown>, now: Date): { pickup: Pickup; error?: never } | { pickup?: never; error: { field: string; message: string } } {
  const limits = { name: 40, phone: 20, address: 150, amount: 15, date: 10, timeSlot: 15, pickupMethod: 15, message: 350 };
  const titles: Record<string, string> = { name: '성함', phone: '연락처', address: '수거 주소', amount: '예상 수거량', date: '희망 날짜', timeSlot: '희망 시간대', pickupMethod: '수거 방식', message: '문의 내용' };
  const fail = (field: string, message: string) => ({ error: { field, message } });
  const fields: Record<string, string> = {};
  for (const [key, limit] of Object.entries(limits)) {
    // FormData serializes textarea line breaks as CRLF. Preserve normal paragraphs.
    const raw = data[key];
    const value = typeof raw === 'string' && key === 'message' ? raw.replace(/\r\n?/g, '\n') : raw;
    if (typeof value !== 'string') return fail(key, `${titles[key]} 항목을 입력해 주세요.`);
    if (value.length > limit) return fail(key, `${titles[key]} 항목은 ${limit}자 이내로 입력해 주세요.`);
    if (/[\u0000-\u0008\u000b-\u001f\u007f]/.test(value)) return fail(key, `${titles[key]} 항목에 사용할 수 없는 문자가 있습니다. 다시 입력해 주세요.`);
    fields[key] = value.trim();
    if (key !== 'message' && (!fields[key] || /[\r\n]/.test(value))) return fail(key, `${titles[key]} 항목을 확인해 주세요.`);
  }
  fields.phone = fields.phone.replace(/[\s.()\-\u2010-\u2015]/g, '').replace(/^\+82(?:0)?/, '0');
  if (!/^01[016789]\d{7,8}$/.test(fields.phone)) return fail('phone', '연락처를 휴대폰 번호로 입력해 주세요. 예: 010-1234-5678');
  if (!amounts.has(fields.amount)) return fail('amount', '예상 수거량을 다시 선택해 주세요.');
  if (!timeSlots.has(fields.timeSlot)) return fail('timeSlot', '희망 시간대를 선택해 주세요.');
  if (!pickupMethods.has(fields.pickupMethod)) return fail('pickupMethod', '대면 또는 비대면 수거 방식을 선택해 주세요.');
  if (data.consent !== true) return fail('consent', '개인정보 수집 및 이용에 동의해 주세요.');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(fields.date)) return fail('date', '희망 날짜를 달력에서 다시 선택해 주세요.');
  const day = new Date(`${fields.date}T00:00:00Z`);
  const todayKst = new Date(now.getTime() + 9 * 3600000).toISOString().slice(0, 10);
  const lastDate = new Date(new Date(`${todayKst}T00:00:00Z`).getTime() + 30 * 86400000).toISOString().slice(0, 10);
  if (!Number.isFinite(day.getTime()) || day.toISOString().slice(0, 10) !== fields.date) return fail('date', '올바른 희망 날짜를 선택해 주세요.');
  if (fields.date <= todayKst || fields.date > lastDate) return fail('date', '희망 날짜는 내일부터 30일 이내로 선택해 주세요.');
  if (day.getUTCDay() === 0) return fail('date', '일요일은 운영하지 않습니다. 월요일부터 토요일 사이의 날짜를 선택해 주세요.');
  return { pickup: fields as Pickup };
}

export function parsePickup(data: Record<string, unknown>, now: Date): Pickup | null {
  return validatePickup(data, now).pickup || null;
}

export async function limitedJson(request: Request, maxBytes = 8192) {
  const reader = request.body?.getReader();
  if (!reader) throw new Error('empty');
  const chunks: Uint8Array[] = [];
  let length = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) break;
      length += value.byteLength;
      if (length > maxBytes) { await reader.cancel(); throw new Error('large'); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
  const result = JSON.parse(new TextDecoder().decode(bytes));
  if (!result || typeof result !== 'object' || Array.isArray(result)) throw new Error('invalid');
  return result as Record<string, unknown>;
}

export function pickupConfiguration(env: Environment) {
  const config = settings(env);
  return reply(200, config ? { enabled: true, siteKey: config.siteKey } : { enabled: false, message: unavailable });
}

export async function submitPickup(request: Request, env: Environment, send: typeof fetch = fetch, now = new Date()) {
  const config = settings(env);
  if (!config) return reply(503, { message: unavailable });
  if (request.headers.get('origin') !== config.origin.origin) return reply(403, { message: '홈페이지 신청서에서 다시 접수해 주세요.' });
  if (!request.headers.get('content-type')?.startsWith('application/json')) return reply(415, { message: '신청 형식을 확인해 주세요.' });
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return reply(400, { message: '신청 내용이 너무 길거나 올바르지 않습니다.' }); }
  const validation = validatePickup(data, now);
  if (validation.error) return reply(400, validation.error);
  const pickup = validation.pickup;
  if (typeof data.token !== 'string' || !data.token || data.token.length > 2048) return reply(400, { message: '자동 입력 방지 확인을 완료해 주세요.' });

  // Turnstile tokens are single-use: replays cannot trigger another paid send.
  try {
    const verification = await send('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ secret: config.captchaSecret, response: data.token }), signal: AbortSignal.timeout(8000),
    });
    const verified = await verification.json() as { success?: boolean; hostname?: string; action?: string };
    if (!verification.ok || !verified.success || verified.hostname !== config.origin.hostname || verified.action !== 'pickup-request') {
      return reply(400, { message: '자동 입력 방지 확인이 만료되었거나 유효하지 않습니다. 다시 확인해 주세요.' });
    }
  } catch { return reply(503, { message: '자동 입력 방지 확인에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.' }); }

  const text = `[헌옷일번지 수거 신청]\n성함: ${pickup.name}\n연락처: ${pickup.phone}\n주소: ${pickup.address}\n수거량: ${pickup.amount}\n희망일: ${pickup.date}\n희망 시간대: ${pickup.timeSlot}\n수거 방식: ${pickup.pickupMethod}\n문의: ${pickup.message || '없음'}\n※ 상담 후 방문 일정 확정`;
  if (new TextEncoder().encode(text).length > 2000) return reply(400, { message: '주소나 문의 내용을 조금 짧게 적어 주세요.' });
  const date = now.toISOString();
  const salt = crypto.randomUUID().replaceAll('-', '');
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(config.secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const signed = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(date + salt));
  const signature = Array.from(new Uint8Array(signed), byte => byte.toString(16).padStart(2, '0')).join('');
  try {
    const response = await send('https://api.solapi.com/messages/v4/send-many/detail', {
      method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `HMAC-SHA256 apiKey=${config.apiKey}, date=${date}, salt=${salt}, signature=${signature}` },
      body: JSON.stringify({ messages: [{ to: config.to, from: config.from, type: 'LMS', subject: '헌옷일번지 수거 신청', text }], showMessageList: true }),
      signal: AbortSignal.timeout(12000),
    });
    if (!response.ok) {
      if (response.status >= 500) return reply(502, { message: uncertain, uncertain: true });
      return reply(502, { message: '문자 접수가 거절되었습니다. 작성 내용은 유지됩니다. 010-4880-8259로 전화해 주세요.' });
    }
    const result = await response.json() as { messageList?: { messageId?: string; statusCode?: string }[]; failedMessageList?: unknown[] };
    if (result.failedMessageList?.length) return reply(502, { message: '문자 접수에 실패했습니다. 010-4880-8259로 전화해 주세요.' });
    const accepted = result.messageList?.[0];
    if (!accepted?.messageId || accepted.statusCode !== '2000') return reply(502, { message: uncertain, uncertain: true });
    // Provider acceptance is not a carrier delivery receipt or an appointment.
    return reply(200, { accepted: true, message: '신청 내용의 문자 발송이 접수되었습니다. 담당자가 연락드려 방문 일정을 확정합니다.' });
  } catch {
    // Never automatically retry a paid send with an unknown outcome.
    return reply(502, { message: uncertain, uncertain: true });
  }
}
