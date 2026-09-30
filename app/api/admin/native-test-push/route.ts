import { adminReady, notifyAdmins, privateJson, validBearer } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';

export async function POST(request: Request) {
  const db = pickupDb();
  const settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 앱 설정이 완료되지 않았습니다.' }, 503);
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET)) return privateJson({ message: '다시 로그인해 주세요.' }, 401);
  try {
    const result = await notifyAdmins(db!, settings);
    if (!result.devices) return privateJson({ message: '등록된 기기가 없거나 Firebase 설정이 없습니다.' }, 400);
    if (!result.accepted) return privateJson({ message: '알림 서버가 요청을 받지 않았습니다.' }, 502);
    return privateJson({ accepted: result.accepted, message: '테스트 알림 전송을 요청했습니다.' });
  } catch { return privateJson({ message: '테스트 알림을 보내지 못했습니다.' }, 503); }
}
