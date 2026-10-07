import { adminReady, privateJson, validBearer } from '@/lib/admin';
import { listDayMemos, saveDayMemo, validPlannerDate } from '@/lib/admin-planner';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { limitedJson } from '@/lib/pickup';

export const dynamic = 'force-dynamic';
async function authorize(request: Request) {
  const db = pickupDb();
  if (!adminReady(serverSettings(), db)) return { error: privateJson({ message: '관리자 설정을 확인해 주세요.' }, 503) };
  if (!await validBearer(request, serverSettings().ADMIN_SESSION_SECRET))
    return { error: privateJson({ message: '다시 로그인해 주세요.' }, 401) };
  return { db: db! };
}
export async function GET(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  try { return privateJson({ memos: await listDayMemos(auth.db!) }); }
  catch { return privateJson({ message: '날짜 메모를 불러오지 못했습니다.' }, 503); }
}
export async function POST(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식을 확인해 주세요.' }, 400); }
  if (!validPlannerDate(data.date) || typeof data.content !== 'string' || data.content.length > 2000 ||
    /[\u0000-\u0008\u000b-\u001f\u007f]/.test(data.content))
    return privateJson({ message: '날짜와 메모 내용(2,000자 이하)을 확인해 주세요.' }, 400);
  try {
    await saveDayMemo(auth.db!, data.date, data.content.trim());
    return privateJson({ saved: true });
  } catch { return privateJson({ message: '날짜 메모를 저장하지 못했습니다.' }, 503); }
}
