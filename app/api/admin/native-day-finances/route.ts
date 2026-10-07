import { adminReady, privateJson, validBearer } from '@/lib/admin';
import { listDayFinances, parseDayFinance, saveDayFinance, validFinanceMonth } from '@/lib/admin-finances';
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
  const month = new URL(request.url).searchParams.get('month');
  if (!validFinanceMonth(month)) return privateJson({ message: '조회할 달을 확인해 주세요.' }, 400);
  try { return privateJson({ records: await listDayFinances(auth.db!, month) }); }
  catch { return privateJson({ message: '금액 기록을 불러오지 못했습니다.' }, 503); }
}
export async function POST(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식을 확인해 주세요.' }, 400); }
  const record = parseDayFinance(data);
  if (!record) return privateJson({ message: '날짜, 금액(0 이상의 원 단위 정수), 메모(2,000자 이하)를 확인해 주세요.' }, 400);
  try { await saveDayFinance(auth.db!, record); return privateJson({ saved: true }); }
  catch { return privateJson({ message: '하루 기록을 저장하지 못했습니다. 다시 시도해 주세요.' }, 503); }
}
