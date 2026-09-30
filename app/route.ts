import homepage from '../public/html/index.html?raw';

export function GET() {
  return new Response(homepage, {
    headers: { 'Content-Type': 'text/html; charset=utf-8' },
  });
}
