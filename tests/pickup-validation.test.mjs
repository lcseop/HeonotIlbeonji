import test from 'node:test';
import assert from 'node:assert/strict';
import { validatePickup } from '../lib/pickup.ts';
const now=new Date('2026-10-17T01:00:00Z');
const valid={name:'테스트',phone:'010-1234-5678',address:'테스트 주소',amount:'20~30kg',date:'2026-10-19',timeSlot:'오후',pickupMethod:'비대면 수거',message:'',consent:true};
test('each rejected input identifies its own field and does not blame unrelated date or consent',()=>{
 for(const [change,field,text] of [
   [{phone:'12345'},'phone','휴대폰'],[{date:'2026-10-17'},'date','내일부터'],[{date:'2026-11-17'},'date','30일'],
   [{date:'2026-10-18'},'date','일요일'],[{date:'2026-11-31'},'date','올바른'],[{consent:false},'consent','동의'],
   [{name:''},'name','성함'],[{address:''},'address','주소'],[{amount:'기타'},'amount','수거량'],[{timeSlot:''},'timeSlot','시간대'],
   [{pickupMethod:'우편'},'pickupMethod','수거 방식'],[{message:'가'.repeat(351)},'message','350자'],
 ]) {const result=validatePickup({...valid,...change},now);assert.equal(result.error.field,field);assert.ok(result.error.message.includes(text),result.error.message);if(field==='phone')assert.doesNotMatch(result.error.message,/날짜|동의/);}
});
test('normalizes common mobile autofill phone formats and textarea CRLF without accepting invalid numbers',()=>{
 for(const phone of ['01012345678','010.1234.5678','010 1234 5678','010–1234–5678','+82 10-1234-5678','+82 (0)10 1234 5678']) {
   const result=validatePickup({...valid,phone,message:'헌옷 25kg\r\n책 3묶음\r\n비대면 희망'},now);
   assert.equal(result.pickup.phone,'01012345678');assert.equal(result.pickup.message,'헌옷 25kg\n책 3묶음\n비대면 희망');
 }
 for(const phone of ['+1 01012345678','010123456789','010abc12345678','0101234\n5678'])assert.equal(validatePickup({...valid,phone},now).error.field,'phone');
 assert.equal(validatePickup({...valid,name:'다른\n연락처'},now).error.field,'name');
});
test('valid dates still pass at Korean midnight and through the inclusive 30-day boundary',()=>{
 for(const date of ['2026-10-19','2026-10-26','2026-11-16'])assert.equal(validatePickup({...valid,date},now).pickup.date,date);
 assert.equal(validatePickup({...valid,date:'2026-10-17'},new Date('2026-10-16T14:59:59Z')).pickup.date,'2026-10-17');
 assert.equal(validatePickup({...valid,date:'2026-10-17'},new Date('2026-10-16T15:00:00Z')).error.field,'date');
});
