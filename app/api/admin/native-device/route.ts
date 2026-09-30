import { addDeviceToken, adminReady, privateJson, removeDeviceToken, validBearer } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { limitedJson } from '@/lib/pickup';

async function handle(request: Request, remove: boolean) {
  const db = pickupDb();
  const settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 앱 설정이 완료되지 않았습니다.' }, 503);
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET)) return privateJson({ message: '다시 로그인해 주세요.' }, 401);
  let token: unknown;
  try { token = (await limitedJson(request)).token; }
  catch { return privateJson({ message: '기기 정보를 확인해 주세요.' }, 400); }
  if (typeof token !== 'string' || token.length < 20 || token.length > 4096 || !/^[A-Za-z0-9:_-]+$/.test(token))
    return privateJson({ message: '기기 정보를 확인해 주세요.' }, 400);
  try {
    if (remove) await removeDeviceToken(db!, token);
    else await addDeviceToken(db!, token);
    return privateJson({ saved: true });
  } catch { return privateJson({ message: '기기 등록에 실패했습니다.' }, 503); }
}

export function POST(request: Request) { return handle(request, false); }
export function DELETE(request: Request) { return handle(request, true); }
