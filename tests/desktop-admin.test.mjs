import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
import { recordWebsiteEvent, parseWebsiteEvent } from '../lib/website-stats.ts';
import { savePickup, listPickups, ensureTables } from '../lib/admin.ts';
import { prepareDesktop, readDesktopTable, desktopOverview, desktopQuery, desktopTables } from '../lib/desktop-admin.ts';
function database(){const sql=new DatabaseSync(':memory:');return{sql,db:{prepare(query){const s=sql.prepare(query);const b=args=>({run:async()=>({meta:{changes:Number(s.run(...args).changes)}}),all:async()=>({results:s.all(...args)})});return{...b([]),bind:(...args)=>b(args)};},async batch(list){sql.exec('BEGIN');try{const out=[];for(const s of list)out.push(await s.run());sql.exec('COMMIT');return out;}catch(e){sql.exec('ROLLBACK');throw e;}}}};}
const now=new Date('2026-10-18T03:00:00Z');
test('daily visitors deduplicate page reloads and source counters reject event replays',async()=>{
 const {db,sql}=database();try{const visitor=crypto.randomUUID(),id=crypto.randomUUID();
 await recordWebsiteEvent(db,{id,type:'view',source:'google_ads',visitor},now);
 await recordWebsiteEvent(db,{id,type:'view',source:'naver_ads',visitor:crypto.randomUUID()},now);
 await recordWebsiteEvent(db,{id:crypto.randomUUID(),type:'view',source:'google_ads',visitor},now);
 await recordWebsiteEvent(db,{id:crypto.randomUUID(),type:'phone',source:'google_ads'},now);
 await recordWebsiteEvent(db,{id:crypto.randomUUID(),type:'view',source:'naver_ads',visitor:crypto.randomUUID()},now);
 const result=await desktopOverview(db,'2026-10',{PICKUP_DELIVERY_MODE:'dashboard',TURNSTILE_SITE_KEY:'fixture',TURNSTILE_SECRET_KEY:'fixture',SITE_ORIGIN:'https://1beonji.com'});
 assert.equal(result.sources.find(r=>r.source==='google_ads').views,2);assert.equal(result.sources.find(r=>r.source==='google_ads').phoneClicks,1);assert.equal(result.sources.find(r=>r.source==='naver_ads').views,1);
 assert.equal(result.visitors[0].visitors,2);const hash=sql.prepare('SELECT visitor FROM website_visitor_keys LIMIT 1').get().visitor;assert.match(hash,/^[a-f0-9]{64}$/);assert.notEqual(hash,visitor);
 await recordWebsiteEvent(db,{id:crypto.randomUUID(),type:'view',source:'direct',visitor},new Date(now.getTime()+86400000));
 assert.equal(sql.prepare('SELECT SUM(visitors) AS total FROM website_daily_visitors').get().total,3);
 }finally{sql.close();}
});
test('database views are whitelisted and paginated, excluding credentials and visitor identifiers',async()=>{
 const {db,sql}=database();try{await prepareDesktop(db);
 sql.prepare('INSERT INTO calendar_memos (date,content,updated_at) VALUES (?,?,?)').run('2026-10-18','테스트',1);
 const result=await readDesktopTable(db,'day_notes',0);assert.equal(result.total,1);assert.equal(result.rows[0].content,'테스트');assert.equal(result.pageSize,200);
 for(const name of ['admin_devices','admin_login_attempts','website_visitor_keys','pickup_requests;DROP TABLE pickup_requests'])assert.equal(desktopQuery(new URL('https://example.com?table='+encodeURIComponent(name))),null);
 assert.equal(desktopQuery(new URL('https://example.com?page=-1')),null);assert.equal(desktopQuery(new URL('https://example.com?page=1.5')),null);
 assert.equal(Object.keys(desktopTables).length,8);
 }finally{sql.close();}
});
test('analytics validation only accepts known categories and random visitor IDs',()=>{
 const id=crypto.randomUUID();assert.equal(parseWebsiteEvent({id,type:'view',source:'raw keyword',visitor:id}),null);assert.equal(parseWebsiteEvent({id,type:'view',source:'google_ads',visitor:'01012345678'}),null);assert.equal(parseWebsiteEvent({id,type:'view',source:'google_ads',visitor:id}).source,'google_ads');
});
const script=readFileSync('public/js/website-stats.js','utf8');
function browser(url,referrer='',storage=new Map(),session=new Map()){
 const requests=[];const listeners={};let index=0;const store=map=>({getItem:key=>map.get(key)||null,setItem:(key,value)=>map.set(key,value)});
 class Clock extends Date{static now(){return now.getTime();}}
 const context={URL,location:{href:url},document:{referrer,addEventListener:(key,fn)=>listeners[key]=fn},localStorage:store(storage),sessionStorage:store(session),Date:Clock,crypto:{randomUUID:()=>crypto.randomUUID()},Blob,navigator:{sendBeacon:(url,body)=>{requests.push(body);return true;}}};vm.runInNewContext(script,context);
 return {requests,listeners,storage,session,source:context.heonotTrafficSource()};
}
test('Google and Naver ads and organic referrals are distinguished without sending URLs or terms',async()=>{
 for(const [url,referrer,source]of [['https://1beonji.com/?gclid=secret&query=private','','google_ads'],['https://1beonji.com/?n_media=1&n_query=private','','naver_ads'],['https://1beonji.com/?utm_source=naver&utm_medium=cpc','','naver_ads'],['https://1beonji.com/','https://blog.naver.com/anything','naver_blog'],['https://1beonji.com/','https://www.google.com/search?q=private','google_search'],['https://1beonji.com/','','direct']]){
   const page=browser(url,referrer);const data=JSON.parse(await page.requests[0].text());assert.equal(data.source,source);assert.equal(page.source,source);assert.match(data.visitor,/^[a-f0-9-]{36}$/);assert.equal(data.url,undefined);assert.equal(data.query,undefined);assert.ok((await page.requests[0].text()).length<=256);
   const repeat=browser('https://1beonji.com/','',page.storage,page.session);const next=JSON.parse(await repeat.requests[0].text());assert.equal(next.visitor,data.visitor);assert.equal(next.source,source);
 }
});

test('new requests retain coarse attribution, old rows remain unknown and retries cannot overwrite it',async()=>{
 const {db,sql}=database();try{
  // Simulate a pre-attribution database and an existing customer record.
  sql.exec(`CREATE TABLE pickup_requests (id TEXT PRIMARY KEY, created_at INTEGER NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, amount TEXT NOT NULL, date TEXT NOT NULL, message TEXT NOT NULL, status TEXT NOT NULL DEFAULT 'new');
  INSERT INTO pickup_requests VALUES ('old',1792416117808,'example','01000000000','example','20kg','2026-10-20','','new');`);
  await ensureTables(db);
  const pickup={name:'example',phone:'01000000000',address:'example',amount:'20kg',date:'2026-10-20',timeSlot:'오후',pickupMethod:'비대면 수거',message:''};
  await savePickup(db,pickup,'ads',true,'google_ads');
  await savePickup(db,pickup,'ads',true,'naver_ads');
  await savePickup(db,pickup,'manual',false,'google_ads');
  await savePickup(db,pickup,'invalid',true,'https://private.example/?phone=secret');
  const rows=await listPickups(db);
  assert.equal(rows.find(r=>r.id==='old').trafficSource,'unknown');
  assert.equal(rows.find(r=>r.id==='ads').trafficSource,'google_ads');
  assert.equal(rows.find(r=>r.id==='manual').trafficSource,'manual');
  assert.equal(rows.find(r=>r.id==='invalid').trafficSource,'unknown');
  const table=await readDesktopTable(db,'requests',0);
  assert.ok(table.columns.includes('traffic_source'));
 }finally{sql.close();}
});
