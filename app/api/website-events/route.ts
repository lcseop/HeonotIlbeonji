import { pickupDb, serverSettings } from '@/lib/admin-runtime';
import { allowedWebsiteRequest, parseWebsiteEvent, recordWebsiteEvent } from '@/lib/website-stats';
import { limitedJson } from '@/lib/pickup';
export const dynamic = 'force-dynamic';
export async function POST(request: Request) {
  const db = pickupDb();
  if (!db || !allowedWebsiteRequest(request, serverSettings().SITE_ORIGIN))
    return new Response(null, { status: 403 });
  let data: Record<string, unknown>;
  try {
    data = await limitedJson(request, 256);
  } catch { return new Response(null, { status: 400 }); }
  const event = parseWebsiteEvent(data);
  if (!event) return new Response(null, { status: 400 });
  try { await recordWebsiteEvent(db, event); }
  catch { return new Response(null, { status: 503 }); }
  return new Response(null, { status: 204, headers: { 'Cache-Control': 'no-store' } });
}
