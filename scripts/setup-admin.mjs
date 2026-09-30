import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';

const path = new URL('../.env.local', import.meta.url);
const original = existsSync(path) ? readFileSync(path, 'utf8') : '';
const lines = original.split(/\r?\n/).filter(Boolean);
const fields = new Map(lines.filter(line => /^[A-Z0-9_]+=/.test(line)).map(line => {
  const equal = line.indexOf('=');
  return [line.slice(0, equal), line.slice(equal + 1)];
}));
const set = (name, value, replace = false) => {
  if (!replace && fields.get(name)) return;
  const index = lines.findIndex(line => line.startsWith(`${name}=`));
  if (index >= 0) lines[index] = `${name}=${value}`;
  else lines.push(`${name}=${value}`);
  fields.set(name, value);
};

set('PICKUP_DELIVERY_MODE', 'dashboard', true);
set('PICKUP_SMS_ENABLED', 'false', true);
set('ADMIN_PASSWORD', randomBytes(18).toString('base64url'));
set('ADMIN_SESSION_SECRET', randomBytes(48).toString('base64url'));
writeFileSync(path, lines.join('\n') + '\n', { encoding: 'utf8', mode: 0o600 });
console.log('관리자 설정을 .env.local에 저장했습니다. 비밀번호와 비밀키는 화면에 표시하지 않았습니다.');
