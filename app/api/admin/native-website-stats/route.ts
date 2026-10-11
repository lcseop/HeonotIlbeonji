import { adminReady, privateJson, validBearer } from '@/lib/admin';
import { validFinanceMonth } from '@/lib/admin-finances';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { listWebsiteStats } from '@/lib/website-stats';
export const dynamic = 'force-dynamic';
export async function GET(request: Request) {
  const db = pickupDb(), settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 설정을 확인해 주세요.' }, 503);
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET))
    return privateJson({ message: '다시 로그인해 주세요.' }, 401);
  const month = new URL(request.url).searchParams.get('month');
  if (!validFinanceMonth(month)) return privateJson({ message: '조회할 달을 확인해 주세요.' }, 400);
  try { return privateJson(await listWebsiteStats(db!, month)); }
  catch { return privateJson({ message: '홈페이지 통계를 불러오지 못했습니다.' }, 503); }
}
