import { adminReady, notifyAdmins, privateJson, savePickup } from './admin.ts';
import { limitedJson, parsePickup } from './pickup.ts';

const unavailable = '현재 온라인 접수가 어렵습니다. 010-4880-8259로 전화해 주세요.';

function config(env: NodeJS.ProcessEnv, db?: D1Database) {
  if (!adminReady(env, db) || !env.SITE_ORIGIN || !env.TURNSTILE_SITE_KEY || !env.TURNSTILE_SECRET_KEY) return null;
  try {
    const origin = new URL(env.SITE_ORIGIN);
    if (origin.protocol !== 'https:' && !['localhost', '127.0.0.1'].includes(origin.hostname)) return null;
    return { origin, siteKey: env.TURNSTILE_SITE_KEY, captchaSecret: env.TURNSTILE_SECRET_KEY, db: db! };
  } catch { return null; }
}

export function dashboardConfiguration(env: NodeJS.ProcessEnv, db?: D1Database) {
  const settings = config(env, db);

  if (!settings) {
    return privateJson({
      enabled: false,
      debug: {
        db: !!db,
        adminPassword: !!env.ADMIN_PASSWORD,
        adminPasswordLength: env.ADMIN_PASSWORD?.length ?? 0,
        adminSessionSecret: !!env.ADMIN_SESSION_SECRET,
        adminSessionSecretLength: env.ADMIN_SESSION_SECRET?.length ?? 0,
        siteOrigin: !!env.SITE_ORIGIN,
        siteOriginValid: (() => {
          try {
            const origin = new URL(env.SITE_ORIGIN || '');
            return origin.protocol === 'https:' ||
              ['localhost', '127.0.0.1'].includes(origin.hostname);
          } catch {
            return false;
          }
        })(),
        turnstileSiteKey: !!env.TURNSTILE_SITE_KEY,
        turnstileSecretKey: !!env.TURNSTILE_SECRET_KEY
      },
      message: unavailable
    });
  }

  return privateJson({
    enabled: true,
    siteKey: settings.siteKey,
    delivery: 'dashboard'
  });
}

export async function submitDashboardPickup(request: Request, env: NodeJS.ProcessEnv, db?: D1Database,
  send: typeof fetch = fetch, now = new Date()) {
  const settings = config(env, db);
  if (!settings) return privateJson({ message: unavailable }, 503);
  if (request.headers.get('origin') !== settings.origin.origin) return privateJson({ message: '홈페이지 신청서에서 다시 접수해 주세요.' }, 403);
  if (!request.headers.get('content-type')?.startsWith('application/json')) return privateJson({ message: '신청 형식을 확인해 주세요.' }, 415);
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '신청 내용이 너무 길거나 올바르지 않습니다.' }, 400); }
  const pickup = parsePickup(data, now);
  if (!pickup) return privateJson({ message: '입력 내용과 개인정보 동의를 확인해 주세요. 방문 날짜는 내일부터 90일 이내, 월~토로 선택해 주세요.' }, 400);
  if (typeof data.token !== 'string' || !data.token || data.token.length > 2048) return privateJson({ message: '자동 입력 방지 확인을 완료해 주세요.' }, 400);

  try {
    const verification = await send('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ secret: settings.captchaSecret, response: data.token }), signal: AbortSignal.timeout(8000),
    });
    const result = await verification.json() as { success?: boolean; hostname?: string; action?: string };
    if (!verification.ok || !result.success || result.hostname !== settings.origin.hostname || result.action !== 'pickup-request') {
      return privateJson({ message: '자동 입력 방지 확인이 만료되었거나 유효하지 않습니다. 다시 확인해 주세요.' }, 400);
    }
  } catch { return privateJson({ message: '자동 입력 방지 확인에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.' }, 503); }

  try {
    await savePickup(settings.db, pickup);
  } catch {
    return privateJson({ message: '신청 내용을 저장하지 못했습니다. 잠시 후 다시 시도하거나 전화해 주세요.' }, 503);
  }
  try { await notifyAdmins(settings.db, env, send); }
  catch { /* The request is saved; notification failures must not invite duplicate submissions. */ }
  return privateJson({ accepted: true, message: '신청이 접수되었습니다. 담당자가 연락드려 방문 일정을 확정합니다.' });
}
