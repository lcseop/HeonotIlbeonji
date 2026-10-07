import release from '@/public/admin-app/latest.json';

// Only version and download information is public; admin data still requires login.
export const dynamic = 'force-dynamic';
export function GET() {
  return Response.json(release, { headers: { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
}
