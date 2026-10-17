import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import { readFileSync } from 'node:fs';
test('renders Korean weekdays for valid dates without empty parentheses, including after reset',()=>{
 const source=readFileSync(new URL('../public/js/pickup.js',import.meta.url),'utf8');const handlers={},classes=new Set(),timers=[];
 const date={value:'2026-10-26',parentElement:{classList:{add:()=>{}}},addEventListener:(event,fn)=>handlers[event]=fn};
 const output={classList:{toggle:(name,on)=>on?classes.add(name):classes.delete(name)}};
 const form={elements:{date},querySelector:selector=>selector==='[type="submit"]'?{}:output,addEventListener:(event,fn)=>handlers['form-'+event]=fn};
 const notice={classList:{toggle:()=>{}}};
 vm.runInNewContext(source,{document:{querySelector:selector=>selector==='#pickupForm'?form:notice},window:{},Date,AbortSignal,setTimeout:fn=>timers.push(fn),fetch:async()=>({ok:false})});
 assert.equal(output.textContent,'2026-10-26 (월)');
 assert.equal((new Date(date.max+'T00:00:00Z')-new Date(date.min+'T00:00:00Z'))/86400000,29,'Tomorrow through day 30, inclusive');
 for(const [value,expected]of [['2026-10-25','2026-10-25 (일)'],['2026-10-31','2026-10-31 (토)'],['2028-02-29','2028-02-29 (화)'],['2026-02-30','날짜 선택'],['','날짜 선택']]) {date.value=value;handlers.change();assert.equal(output.textContent,expected);}
 date.value='2026-10-26';handlers.input();date.value='';handlers['form-reset']();timers.shift()();assert.equal(output.textContent,'날짜 선택');
});
