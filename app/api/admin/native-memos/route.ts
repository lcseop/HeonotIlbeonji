import { adminReady, deleteMemo, listMemos, privateJson, saveMemo, validBearer } from '@/lib/admin';
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
  try { return privateJson({ memos: await listMemos(auth.db!) }); }
  catch { return privateJson({ message: '메모를 불러오지 못했습니다.' }, 503); }
}

export async function POST(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 400); }
  const phone = typeof data.phone === 'string' ? data.phone.replace(/[\s()-]/g, '').replace(/^\+82/, '0') : '';
  const name = typeof data.name === 'string' ? data.name.trim() : '';
  const title = typeof data.title === 'string' ? data.title.trim() : '';
  const content = typeof data.content === 'string' ? data.content.trim() : '';
  const id = data.id;
  if (!/^0\d{8,10}$/.test(phone) || !name || name.length > 40 || !title || title.length > 80 ||
      !content || content.length > 2000 || /[\u0000-\u0008\u000b-\u001f\u007f]/.test(`${name}${title}${content}`) ||
      (id !== undefined && (typeof id !== 'string' || !/^[0-9a-f-]{36}$/i.test(id))))
    return privateJson({ message: '이름, 전화번호, 제목, 내용을 확인해 주세요.' }, 400);
  try {
    const saved = await saveMemo(auth.db!, { phone, name, title, content }, id as string | undefined);
    return privateJson(saved ? { id: saved } : { message: '메모를 찾지 못했습니다.' }, saved ? 200 : 404);
  } catch { return privateJson({ message: '메모를 저장하지 못했습니다.' }, 503); }
}

export async function DELETE(request: Request) {
  const auth = await authorize(request);
  if (auth.error) return auth.error;
  let data: Record<string, unknown>;
  try { data = await limitedJson(request); }
  catch { return privateJson({ message: '입력 형식이 올바르지 않습니다.' }, 400); }
  if (typeof data.id !== 'string' || !/^[0-9a-f-]{36}$/i.test(data.id))
    return privateJson({ message: '메모를 확인해 주세요.' }, 400);
  try {
    const deleted = await deleteMemo(auth.db!, data.id);
    return privateJson(deleted ? { deleted: true } : { message: '메모를 찾지 못했습니다.' }, deleted ? 200 : 404);
  } catch { return privateJson({ message: '메모를 삭제하지 못했습니다.' }, 503); }
}
