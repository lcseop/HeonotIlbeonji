export type MetalQuote = { name: string; grade: string; price: number; unit: string };
export type MetalFeed = { source: string; url: string; asOf: string; basis: string; quotes: MetalQuote[]; fetchedAt?: number; stale?: boolean; error?: string };
const GOLD = 'https://www.koreagoldx.co.kr/';
const SCRAP = 'https://allmetal.co.kr/';
function positive(value: unknown) {
  const number = typeof value === 'number' ? value : typeof value === 'string' && /^\d+(?:\.\d+)?$/.test(value) ? Number(value) : NaN;
  if (!Number.isFinite(number) || number <= 0 || number > 100000000) throw new Error('invalid price');
  return number;
}
function date(value: string) {
  if (!/^20\d{2}-\d{2}-\d{2}$/.test(value)) throw new Error('invalid date');
  const parsed = new Date(value + 'T00:00:00Z');
  if (!Number.isFinite(parsed.getTime()) || parsed.toISOString().slice(0, 10) !== value) throw new Error('invalid date');
  return value;
}
export function parseGoldPrices(input: unknown): MetalFeed {
  const data = (input as { officialPrice4?: Record<string, unknown> })?.officialPrice4;
  if (!data || typeof data.date !== 'string') throw new Error('missing gold prices');
  return { source: '한국금거래소', url: GOLD + 'price/gold', asOf: date(data.date.slice(0, 10)),
    basis: '내가 팔 때 · 금방금방 앱 기준 / 은은 자사 실버바 기준', quotes: [
      { name: '금', grade: '순금 24K', price: positive(data.p_pure), unit: '원 / 1돈 (3.75g)' },
      { name: '은', grade: '자사 실버바', price: positive(data.p_silver), unit: '원 / 1돈 (3.75g)' },
    ] };
}
export function parseKGoldPrices(html: string): MetalFeed {
  // Decode public server-rendered JSON as data; never execute the site's scripts.
  let payload = '';
  for (const match of html.matchAll(/self\.__next_f\.push\((\[[\s\S]*?\])\)<\/script>/g)) {
    const chunk = JSON.parse(match[1]);
    if (typeof chunk[1] === 'string') payload += chunk[1];
  }
  const start = payload.indexOf('"board":{');
  if (start < 0) throw new Error('missing precious metal board');
  const from = start + '"board":'.length;
  let depth = 0, quoted = false, escaped = false, end = -1;
  for (let i = from; i < payload.length; i++) {
    const char = payload[i];
    if (quoted) { if (escaped) escaped = false; else if (char === '\\') escaped = true; else if (char === '"') quoted = false; }
    else if (char === '"') quoted = true;
    else if (char === '{') depth++;
    else if (char === '}' && --depth === 0) { end = i + 1; break; }
  }
  if (end < 0) throw new Error('invalid precious metal board');
  const board = JSON.parse(payload.slice(from, end));
  if (JSON.stringify(board.columns) !== JSON.stringify(['순금', '18K', '14K', '백금', '은']) || !Array.isArray(board.sellRow)) throw new Error('changed precious metal columns');
  const price = (index: number) => positive(board.sellRow[index]?.text?.replaceAll(',', ''));
  return { source: 'K-금은거래소', url: 'https://www.k-goldsilver.com/', asOf: date(board.pricedOn),
    basis: '내가 팔 때 · 금은 골드바/덩어리(검인), 은은 그래뉼 기준', quotes: [
      { name: '금', grade: '순금 · 골드바/덩어리(검인)', price: price(0), unit: '원 / 1돈 (3.75g)' },
      { name: '은', grade: '그래뉼 (순은)', price: price(4), unit: '원 / 1돈 (3.75g)' },
    ] };
}
export function scrapBoardUrl(html: string) {
  const path = html.match(/https:\/\/allmetal\.co\.kr\/bbs\/board\.php\?bo_table=price(?:&amp;|&)wr_id=\d+/)?.[0];
  if (!path) throw new Error('missing latest board');
  return path.replaceAll('&amp;', '&');
}
export function parseScrapPrices(html: string, url: string): MetalFeed {
  const title = html.match(/<h2\b[^>]*id=["']bo_v_title["'][^>]*>([\s\S]*?)<\/h2>/i)?.[1];
  const stamp = title?.match(/(\d{2})\/(\d{2})\/(\d{2})/);
  if (!stamp) throw new Error('missing scrap date');
  const values = new Map<string, number>();
  for (const row of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)) {
    const cells = [...row[1].matchAll(/<td\b[^>]*>([\s\S]*?)<\/td>/gi)].map(cell => cell[1].replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim());
    if (cells.length === 2 && /^[\d,]+$/.test(cells[1])) values.set(cells[0], positive(cells[1].replaceAll(',', '')));
  }
  const grades = [['동', 'A동'], ['동', '상동'], ['동', '파동'], ['스텐', 'STS304'], ['스텐', 'STS316'],
    ['알루미늄', 'AL 샤시'], ['알루미늄', 'AL 프로파일'], ['알루미늄', 'AL 판재'], ['알루미늄', 'AL 주물']];
  const quotes = grades.map(([name, grade]) => ({ name, grade, price: positive(values.get(grade)), unit: '원 / kg' }));
  return { source: '올메탈', url, asOf: date(`20${stamp[1]}-${stamp[2]}-${stamp[3]}`),
    basis: '스크랩 매입 고시가 · 도착도 · 부가세 별도 · 운송/상차 조건에 따라 변동', quotes };
}
async function download(url: string, method = 'GET') {
  const response = await fetch(url, { method, signal: AbortSignal.timeout(7000), headers: { 'Accept': '*/*', 'User-Agent': 'HeonotIlbeonji/1.4.19 (https://1beonji.com)', 'Referer': new URL(url).origin + '/' } });
  if (!response.ok) throw new Error(`source HTTP ${response.status}`);
  return response;
}
export async function getMetalPrices(db: D1Database) {
  await db.prepare('CREATE TABLE IF NOT EXISTS metal_price_cache (source TEXT PRIMARY KEY, content TEXT NOT NULL, fetched_at INTEGER NOT NULL)').run();
  const loaders = [
    { key: 'gold', source: '한국금거래소', url: GOLD + 'price/gold', load: async () => {
      try { return parseGoldPrices(await (await download(GOLD + 'api/main', 'POST')).json()); }
      catch { return parseKGoldPrices(await (await download('https://www.k-goldsilver.com/')).text()); }
    } },
    { key: 'scrap', source: '올메탈', url: SCRAP, load: async () => { const url = scrapBoardUrl(await (await download(SCRAP)).text()); return parseScrapPrices(await (await download(url)).text(), url); } },
  ];
  const feeds = await Promise.all(loaders.map(async loader => {
    const cache = await db.prepare('SELECT content, fetched_at FROM metal_price_cache WHERE source = ?').bind(loader.key).first<{ content: string; fetched_at: number }>();
    const previous = cache ? JSON.parse(cache.content) as MetalFeed : null;
    if (previous && cache && Date.now() - cache.fetched_at < 15 * 60000) return { ...previous, fetchedAt: cache.fetched_at, stale: false };
    try {
      const feed = await loader.load(), now = Date.now();
      await db.prepare('INSERT INTO metal_price_cache (source, content, fetched_at) VALUES (?, ?, ?) ON CONFLICT(source) DO UPDATE SET content=excluded.content, fetched_at=excluded.fetched_at')
        .bind(loader.key, JSON.stringify(feed), now).run();
      return { ...feed, fetchedAt: now, stale: false };
    } catch (issue) {
      return { ...(previous || { source: loader.source, url: loader.url, asOf: '', basis: '', quotes: [] }),
        fetchedAt: cache?.fetched_at || 0, stale: true, sourceError: issue instanceof Error ? issue.message.slice(0, 160) : 'source error', error: previous ? '조회 실패 · 이전에 확인한 가격입니다' : '시세를 불러오지 못했습니다' };
    }
  }));
  return { feeds, checkedAt: Date.now() };
}
