export const dynamic = 'force-dynamic';

export function GET() {
  const key = process.env.KAKAO_MAP_JAVASCRIPT_KEY?.trim() || '';
  return Response.json({ key }, { headers: { 'Cache-Control': 'no-store' } });
}
