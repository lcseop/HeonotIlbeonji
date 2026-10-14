import { adminReady, privateJson, validBearer } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { getMetalPrices } from '@/lib/metal-prices';
export const dynamic = 'force-dynamic';
export async function GET(request: Request) {
  const db = pickupDb(), settings = serverSettings();
  if (!adminReady(settings, db)) return privateJson({ message: '관리자 설정을 확인해 주세요.' }, 503);
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET)) return privateJson({ message: '다시 로그인해 주세요.' }, 401);
  try { return privateJson(await getMetalPrices(db!)); }
  catch { return privateJson({ message: '시세 연결을 확인해 주세요.' }, 503); }
}
