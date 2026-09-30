import { adminReady, clearLoginFailures, loginAllowed, makeSession, passwordMatches,
  privateJson, recordLoginFailure } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { limitedJson } from '@/lib/pickup';

export async function POST(request: Request) {
  const db = pickupDb();
  const settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 앱 설정이 완료되지 않았습니다.' }, 503);
  if (!request.headers.get('content-type')?.startsWith('application/json')) return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 415);
  let password: unknown;
  try { password = (await limitedJson(request)).password; }
  catch { return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 400); }
  if (typeof password !== 'string' || password.length > 200) return privateJson({ message: '비밀번호를 확인해 주세요.' }, 400);
  const ip = request.headers.get('cf-connecting-ip') || 'local';
  try {
    if (!await loginAllowed(db!, ip)) return privateJson({ message: '로그인 시도가 많습니다. 15분 뒤 다시 시도해 주세요.' }, 429);
    if (!await passwordMatches(password, settings.ADMIN_PASSWORD!)) {
      await recordLoginFailure(db!, ip);
      return privateJson({ message: '비밀번호가 올바르지 않습니다.' }, 401);
    }
    await clearLoginFailures(db!, ip);
    return privateJson({ token: await makeSession(settings.ADMIN_SESSION_SECRET!), expiresInDays: 30 });
  } catch { return privateJson({ message: '로그인에 연결하지 못했습니다.' }, 503); }
}
