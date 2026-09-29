import test from 'node:test';
import assert from 'node:assert/strict';
import { createHmac } from 'node:crypto';
import { pickupConfiguration, submitPickup } from '../lib/pickup.ts';

const env = { PICKUP_SMS_ENABLED: 'true', SOLAPI_FROM: '01048808259', SOLAPI_API_KEY: 'test-key', SOLAPI_API_SECRET: 'test-secret', TURNSTILE_SITE_KEY: 'test-public', TURNSTILE_SECRET_KEY: 'test-private', SITE_ORIGIN: 'https://pickup.example' };
const now = new Date('2026-09-29T15:30:00Z'); // September 30 in Korea.
const payload = { name: '테스트', phone: '010-1234-5678', address: '테스트 주소', amount: '20~30kg', date: '2026-10-01', message: '테스트 신청입니다.', consent: true, token: 'test-token' };
function request(data = payload, origin = env.SITE_ORIGIN) {
  return new Request(`${env.SITE_ORIGIN}/api/pickup`, { method: 'POST', headers: { Origin: origin, 'Content-Type': 'application/json' }, body: JSON.stringify(data) });
}
const validCaptcha = { success: true, hostname: 'pickup.example', action: 'pickup-request' };
const accepted = { failedMessageList: [], messageList: [{ messageId: 'test-message', statusCode: '2000' }] };

test('unconfigured form is disabled and exposes no secret values', async () => {
  assert.equal((await pickupConfiguration({}).json()).enabled, false);
  assert.deepEqual(await pickupConfiguration(env).json(), { enabled: true, siteKey: 'test-public' });
  const result = await submitPickup(request(), {}, () => assert.fail('must not send'), now);
  assert.equal(result.status, 503);
});

test('valid request sends one signed LMS only to the server-selected recipient', async () => {
  const calls = [];
  const send = async (url, options) => {
    calls.push(url);
    if (url.includes('siteverify')) return Response.json(validCaptcha);
    const body = JSON.parse(options.body);
    assert.equal(body.messages.length, 1);
    assert.equal(body.messages[0].to, '01048808259');
    assert.equal(body.messages[0].from, env.SOLAPI_FROM);
    assert.equal(body.messages[0].type, 'LMS');
    assert.match(body.messages[0].text, /01012345678/);
    assert.match(body.messages[0].text, /2026-10-01/);
    assert.equal(body.showMessageList, true);
    const match = options.headers.Authorization.match(/date=([^,]+), salt=([^,]+), signature=(\w+)/);
    assert.equal(match[3], createHmac('sha256', env.SOLAPI_API_SECRET).update(match[1] + match[2]).digest('hex'));
    return Response.json(accepted);
  };
  const result = await submitPickup(request({ ...payload, to: '01099999999', from: '01099999999' }), env, send, now);
  assert.equal(result.status, 200);
  assert.equal((await result.json()).accepted, true);
  assert.equal(calls.length, 2);
});

test('cross-origin requests never invoke a service', async () => {
  const result = await submitPickup(request(payload, 'https://other.example'), env, () => assert.fail('must not send'), now);
  assert.equal(result.status, 403);
});

for (const [name, change] of Object.entries({
  'missing consent': { consent: false },
  'invalid phone': { phone: 'not-a-phone' },
  'unrecognized amount': { amount: '아무거나' },
  'outdated below-minimum option': { amount: '10~20kg' },
  'Korean same-day date': { date: '2026-09-30' },
  'Sunday': { date: '2026-10-04' },
  'impossible date': { date: '2026-11-31' },
  'too distant date': { date: '2027-12-01' },
  'too long message': { message: '가'.repeat(351) },
  'field injection': { name: '홍길동\n연락처: 다른번호' },
  'missing verification': { token: '' },
})) {
  test(`rejects ${name} before external requests`, async () => {
    assert.equal((await submitPickup(request({ ...payload, ...change }), env, () => assert.fail('must not send'), now)).status, 400);
  });
}

test('oversized body is rejected even without a content-length header', async () => {
  const result = await submitPickup(request({ ...payload, extra: 'x'.repeat(9000) }), env, () => assert.fail('must not send'), now);
  assert.equal(result.status, 400);
});

for (const verification of [{ success: false, 'error-codes': ['timeout-or-duplicate'] }, { ...validCaptcha, hostname: 'other.example' }, { ...validCaptcha, action: 'other' }]) {
  test(`rejects invalid/replayed verification ${JSON.stringify(verification)}`, async () => {
    let calls = 0;
    const result = await submitPickup(request(), env, async () => { calls++; return Response.json(verification); }, now);
    assert.equal(result.status, 400);
    assert.equal(calls, 1);
  });
}

test('200 response with failed message does not report successful submission', async () => {
  const result = await submitPickup(request(), env, async url => Response.json(url.includes('siteverify') ? validCaptcha : { failedMessageList: [{ statusCode: '3040' }] }), now);
  assert.equal(result.status, 502);
  assert.notEqual((await result.json()).accepted, true);
});

test('provider timeout is marked uncertain and never automatically retried', async () => {
  let sends = 0;
  const result = await submitPickup(request(), env, async url => {
    if (url.includes('siteverify')) return Response.json(validCaptcha);
    sends++;
    throw new Error('timeout, private provider response');
  }, now);
  const body = await result.json();
  assert.equal(sends, 1);
  assert.equal(result.status, 502);
  assert.equal(body.uncertain, true);
  assert.ok(!JSON.stringify(body).includes('private provider'));
});

test('unexpected successful provider response is not assumed accepted', async () => {
  const result = await submitPickup(request(), env, async url => Response.json(url.includes('siteverify') ? validCaptcha : {}), now);
  assert.equal(result.status, 502);
  assert.equal((await result.json()).uncertain, true);
});
