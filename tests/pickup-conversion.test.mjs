import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../public/js/pickup.js', import.meta.url), 'utf8');
async function page(response, tracking = 'available', traffic = undefined, delivery = undefined) {
  const events = [];const payloads=[];
  const listeners = {};
  const classes = new Set();
  const notice = { classList: { toggle(name, enabled) { if (enabled) classes.add(name); else classes.delete(name); }, contains: name => classes.has(name) } };
  const button = {};
  const attrs={};const errors=[];let focused=false,scrolled=false;
  const phone={setAttribute:(name,value)=>{attrs[name]=value;},getAttribute:name=>attrs[name],removeAttribute:name=>{delete attrs[name];},
    closest:()=>({append:error=>errors.push(error)}),focus:()=>{focused=true;},scrollIntoView:()=>{scrolled=true;}};
  const extra = {entryCode:{value:''},accountHolder:{value:''},bankName:{value:''},accountNumber:{value:''}};
  const fieldset={hidden:true,disabled:true};
  const method={value:'',addEventListener:(_,fn)=>{listeners.method=fn;}};
  const form = {
    elements: { date: { value: '2026-10-08', addEventListener() {}, focus() {} }, consent: { checked: true }, pickupMethod:method, namedItem:name=>name==='phone'?phone:extra[name]||null },
    querySelector: selector => selector === '[type="submit"]' ? button : selector === '#contactlessFields' ? fieldset : null, addEventListener: (name, fn) => { listeners[name] = fn; },
    reportValidity: () => true, setAttribute() {}, reset() {},
  };
  const window = { turnstile: {
    render(target, options) { options.callback('verified'); return 1; }, remove() {}, reset() {},
  } };
  if (tracking !== 'absent') window.gtag = (...args) => {
    if (tracking === 'throws') throw new Error('blocked');
    events.push(args);
  };
  const context = {
    heonotTrafficSource:traffic, window, Date, AbortSignal, setTimeout: () => 1, clearTimeout() {},
    FormData: class { get(key) { return key==='pickupMethod'?method.value:extra[key]?.value??'fixture'; } },
    document: {
      querySelector: selector => selector === '#pickupForm' ? form : notice,
      createElement: () => ({setAttribute(){},remove(){this.removed=true;}}), head: { append: script => script.onload() },
    },
    fetch: async (url, options) => options.method === 'POST' ? (payloads.push(JSON.parse(options.body)),response) :
      { ok: true, json: async () => ({ enabled: true, siteKey: 'test', delivery }) },
  };
  vm.runInNewContext(source, context);
  // Complete initialization without contacting any external service.
  await new Promise(resolve => setImmediate(resolve));
  const submit = () => listeners.submit({ preventDefault() {} });
  return { submit, extra, fieldset, select:value=>{method.value=value;listeners.method();}, payloads, events, button, notice, phone, attrs, errors, listeners, focused:()=>focused&&scrolled };
}

test('invalid phone is marked inline, focused, and cleared when corrected without tracking a conversion',async()=>{
  const ui=await page({ok:false,json:async()=>({field:'phone',message:'연락처를 휴대폰 번호로 입력해 주세요.'})});
  await ui.submit();assert.equal(ui.attrs['aria-invalid'],'true');assert.equal(ui.attrs['aria-describedby'],'pickup-error-phone');
  assert.equal(ui.errors[0].textContent,'연락처를 휴대폰 번호로 입력해 주세요.');assert.ok(ui.focused());assert.equal(ui.events.length,0);
  ui.listeners.input({target:ui.phone});assert.equal(ui.attrs['aria-invalid'],undefined);assert.equal(ui.attrs['aria-describedby'],undefined);assert.ok(ui.errors[0].removed);
});

test('accepted request records the supplied conversion once, even with repeated submit', async () => {
  const ui = await page({ ok: true, json: async () => ({ accepted: true, message: '접수 완료' }) });
  await ui.submit();
  await ui.submit();
  assert.deepEqual(JSON.parse(JSON.stringify(ui.events)), [['event', 'conversion', {
    send_to: 'AW-18497578171/Ftq6CNK4l5QdELvJqvRE',
  }]]);
  assert.equal(ui.button.textContent, '신청 전달 완료');
});

test('rejected or uncertain requests do not record a conversion', async () => {
  for (const result of [{ accepted: false }, { uncertain: true }, { accepted: true }]) {
    const ui = await page({ ok: false, json: async () => result });
    await ui.submit();
    assert.equal(ui.events.length, 0);
  }
});

test('missing or blocked tracking does not turn an accepted request into a failure', async () => {
  for (const tracking of ['absent', 'throws']) {
    const ui = await page({ ok: true, json: async () => ({ accepted: true, message: '접수 완료' }) }, tracking);
    await ui.submit();
    assert.equal(ui.notice.textContent, '접수 완료');
    assert.equal(ui.button.textContent, '신청 전달 완료');
  }
});

test('pickup sends the coarse browser category and submits normally if attribution is unavailable',async()=>{
 for(const category of ['google_ads','naver_blog',undefined]){
  const ui=await page({ok:true,json:async()=>({accepted:true})},'absent',category?()=>category:undefined);
  await ui.submit();assert.equal(ui.payloads[0].trafficSource,category||'unknown');
  assert.equal(ui.button.textContent,'신청 전달 완료');
 }
});

test('contactless fields appear only for dashboard non-face-to-face requests and clear when switching away',async()=>{
 const accepted={ok:true,json:async()=>({accepted:true})};
 const ui=await page(accepted,'absent',undefined,'dashboard');
 assert.equal(ui.fieldset.hidden,true);ui.select('비대면 수거');assert.equal(ui.fieldset.hidden,false);assert.equal(ui.fieldset.disabled,false);
 ui.extra.entryCode.value='0012#';ui.select('대면 수거');assert.equal(ui.extra.entryCode.value,'');assert.equal(ui.fieldset.disabled,true);
 await ui.submit();assert.equal(ui.payloads[0].entryCode,undefined);
 const nonFace=await page(accepted,'absent',undefined,'dashboard');nonFace.select('비대면 수거');nonFace.extra.entryCode.value='0012#';nonFace.extra.accountNumber.value='001-234';await nonFace.submit();assert.equal(nonFace.payloads[0].entryCode,'0012#');assert.equal(nonFace.payloads[0].accountNumber,'001-234');
 const sms=await page(accepted,'absent',undefined,'sms');sms.select('비대면 수거');assert.equal(sms.fieldset.hidden,true);
});
