import { adminReady, listPickups, privateJson, setPickupStatus, validBearer } from '@/lib/admin';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { limitedJson } from '@/lib/pickup';

export const dynamic = 'force-dynamic';

async function authorize(request: Request) {
  const db = pickupDb();
  const settings = serverSettings();
  if (!adminReady(settings, db)) return { error: privateJson({ message: '관리자 앱 설정이 완료되지 않았습니다.' }, 503) };
  if (!await validBearer(request, settings.ADMIN_SESSION_SECRET)) return { error: privateJson({ message: '다시 로그인해 주세요.' }, 401) };
  return { db: db! };
}

export async function GET(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  try { return privateJson({ requests: await listPickups(auth.db!) }); }
  catch { return privateJson({ message: '신청 목록을 불러오지 못했습니다.' }, 503); }
}

export async function POST(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 400); }
  if (typeof data.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.id) || !['new', 'contacted', 'done'].includes(String(data.status)))
    return privateJson({ message: '신청 상태를 확인해 주세요.' }, 400);
  try {
    const updated = await setPickupStatus(auth.db!, data.id, data.status as 'new' | 'contacted' | 'done');
    return privateJson(updated ? { updated: true } : { message: '신청 내역을 찾지 못했습니다.' }, updated ? 200 : 404);
  } catch { return privateJson({ message: '상태를 저장하지 못했습니다.' }, 503); }
}
