import { adminReady, privateJson, validBearer } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { validFinanceMonth } from '@/lib/admin-finances';
import { desktopOverview, desktopQuery, prepareDesktop, readDesktopTable } from '@/lib/desktop-admin';
export const dynamic = 'force-dynamic';
export async function GET(request: Request) {
  const db = pickupDb(), settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 설정을 확인해 주세요.' }, 503);
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET)) return privateJson({ message: '다시 로그인해 주세요.' }, 401);
  const url = new URL(request.url);
  try {
    if (url.searchParams.get('mode') === 'data') {
      const query = desktopQuery(url);
      if (!query) return privateJson({ message: '조회할 데이터와 페이지를 확인해 주세요.' }, 400);
      await prepareDesktop(db!);
      return privateJson(await readDesktopTable(db!, query.key, query.page));
    }
    const month = url.searchParams.get('month');
    if (!validFinanceMonth(month)) return privateJson({ message: '조회할 달을 확인해 주세요.' }, 400);
    return privateJson(await desktopOverview(db!, month, settings));
  } catch { return privateJson({ message: '관리자 데이터 연결에 실패했습니다. 잠시 후 새로고침해 주세요.' }, 503); }
}
