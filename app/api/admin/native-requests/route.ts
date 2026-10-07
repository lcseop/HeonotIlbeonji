import { adminReady, deletePickup, listPickups, privateJson, savePickup, setPickupStatus, validBearer } from '@/lib/admin';
import { editManualPickup, parseManualPickup, parseAdminDetails, saveAdminDetails } from '@/lib/admin-planner';
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
  if (data.action === 'adminDetails') {
    const details = parseAdminDetails(data);
    if (!details || typeof data.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.id))
      return privateJson({ message: '예약 시간과 메모를 확인해 주세요.' }, 400);
    try {
      const updated = await saveAdminDetails(auth.db!, data.id, details);
      return privateJson(updated ? { updated: true } : { message: '신청서를 찾지 못했습니다.' }, updated ? 200 : 404);
    } catch { return privateJson({ message: '예약 시간과 메모를 저장하지 못했습니다.' }, 503); }
  }
  if (data.action === 'create' || data.action === 'edit') {
    const pickup = parseManualPickup(data);
    if (!pickup || typeof data.requestId !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.requestId))
      return privateJson({ message: '이름, 전화번호와 방문 정보를 확인해 주세요.' }, 400);
    try {
      if (data.action === 'edit') {
        const updated = await editManualPickup(auth.db!, data.requestId, pickup);
        return privateJson(updated ? { id: data.requestId, updated: true } : { message: '신청서를 찾지 못했습니다.' }, updated ? 200 : 404);
      }
      return privateJson({ id: await savePickup(auth.db!, pickup, data.requestId), created: true });
    } catch { return privateJson({ message: '신청서를 저장하지 못했습니다. 다시 저장해 주세요.' }, 503); }
  }
  if (typeof data.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.id) || !['new', 'contacted', 'done'].includes(String(data.status)))
    return privateJson({ message: '신청 상태를 확인해 주세요.' }, 400);
  try {
    const updated = await setPickupStatus(auth.db!, data.id, data.status as 'new' | 'contacted' | 'done');
    return privateJson(updated ? { updated: true } : { message: '신청 내역을 찾지 못했습니다.' }, updated ? 200 : 404);
  } catch { return privateJson({ message: '상태를 저장하지 못했습니다.' }, 503); }
}

export async function DELETE(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 400); }
  if (typeof data.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.id))
    return privateJson({ message: '신청서를 확인해 주세요.' }, 400);
  try {
    const deleted = await deletePickup(auth.db!, data.id);
    return privateJson(deleted ? { deleted: true } : { message: '신청 내역을 찾지 못했습니다.' }, deleted ? 200 : 404);
  } catch { return privateJson({ message: '신청서를 삭제하지 못했습니다.' }, 503); }
}
