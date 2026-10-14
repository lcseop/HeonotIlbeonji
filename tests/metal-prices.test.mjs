import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { parseGoldPrices, parseScrapPrices, scrapBoardUrl, getMetalPrices } from '../lib/metal-prices.ts';
const gold={officialPrice4:{date:'2026-10-14 16:34:13',p_pure:673000,p_silver:8800,s_pure:783000}};
const grades=['A동','상동','파동','STS304','STS316','AL 샤시','AL 프로파일','AL 판재','AL 주물'];
const board='<h2 id="bo_v_title"><span>26/10/13 고철 시세</span></h2><table>'+grades.map((grade,i)=>`<tr><td>${grade}</td><td>${(1000+i*500).toLocaleString('en-US')}</td></tr>`).join('')+'</table>';
const url='https://allmetal.co.kr/bbs/board.php?bo_table=price&wr_id=166';
test('uses sale prices, correct units and source publication date',()=>{
 const g=parseGoldPrices(gold);assert.equal(g.quotes[0].price,673000);assert.equal(g.quotes[1].price,8800);assert.equal(g.asOf,'2026-10-14');assert.match(g.quotes[0].unit,/3.75g/);
 const s=parseScrapPrices(board,url);assert.equal(s.asOf,'2026-10-13');assert.equal(s.quotes.length,9);assert.equal(s.quotes[3].grade,'STS304');assert.equal(s.quotes[3].price,2500);assert.equal(s.quotes[3].unit,'원 / kg');
 assert.equal(scrapBoardUrl(`<a href="${url.replace('&','&amp;')}">오늘</a>`),url);
});
test('rejects missing, zero, changed or invalid source formats rather than guessing',()=>{
 for(const value of [null,{}, {officialPrice4:{...gold.officialPrice4,p_pure:0}}, {officialPrice4:{...gold.officialPrice4,p_silver:'문의'}}, {officialPrice4:{...gold.officialPrice4,date:'2026-02-30'}}])assert.throws(()=>parseGoldPrices(value));
 assert.throws(()=>parseScrapPrices(board.replace('STS304','STS304 분철'),url));assert.throws(()=>scrapBoardUrl('<a href="https://other.example">'));assert.throws(()=>parseScrapPrices(board.replace('26/10/13','26/02/30'),url));
});
test('caches feeds separately and identifies older prices when one source fails',async()=>{
 const sql=new DatabaseSync(':memory:');const original=globalThis.fetch;let calls=0;
 const db={prepare(query){const stmt=sql.prepare(query);const bound=args=>({run:async()=>stmt.run(...args),first:async()=>stmt.get(...args)||null});return {...bound([]),bind:(...args)=>bound(args)};}};
 globalThis.fetch=async(target)=>{calls++;return new Response(target.endsWith('/api/main')?JSON.stringify(gold):target===url?board:`<a href="${url}">오늘</a>`);};
 try {
   const fresh=await getMetalPrices(db);assert.equal(calls,3);assert.equal(fresh.feeds[0].stale,false);assert.equal(fresh.feeds[1].quotes.length,9);
   await getMetalPrices(db);assert.equal(calls,3);
   sql.exec('UPDATE metal_price_cache SET fetched_at=0');globalThis.fetch=async(target)=>{if(target.endsWith('/api/main'))throw Error('offline');return new Response(target===url?board:`<a href="${url}">오늘</a>`);};
   const partial=await getMetalPrices(db);assert.equal(partial.feeds[0].stale,true);assert.equal(partial.feeds[0].quotes[0].price,673000);assert.equal(partial.feeds[1].stale,false);
   sql.exec('DELETE FROM metal_price_cache');globalThis.fetch=async()=>{throw Error('offline');};const empty=await getMetalPrices(db);assert.equal(empty.feeds[0].quotes.length,0);assert.equal(empty.feeds[1].stale,true);
 }finally{globalThis.fetch=original;sql.close();}
});
