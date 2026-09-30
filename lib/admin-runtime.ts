import { env } from 'cloudflare:workers';

export function pickupDb() {
  return (env as unknown as { PICKUP_DB?: D1Database }).PICKUP_DB;
}

export function serverSettings(): NodeJS.ProcessEnv {
  const result = { ...process.env };
  const bound = env as unknown as Record<string, unknown>;
  for (const name of ['PICKUP_DELIVERY_MODE', 'PICKUP_SMS_ENABLED', 'PICKUP_SMS_TO', 'SOLAPI_FROM',
    'SOLAPI_API_KEY', 'SOLAPI_API_SECRET', 'SITE_ORIGIN', 'TURNSTILE_SITE_KEY', 'TURNSTILE_SECRET_KEY',
    'ADMIN_PASSWORD', 'ADMIN_SESSION_SECRET', 'FCM_SERVICE_ACCOUNT_JSON', 'FCM_SERVICE_ACCOUNT_JSON_BASE64']) {
    if (typeof bound[name] === 'string') result[name] = bound[name];
  }
  return result;
}
