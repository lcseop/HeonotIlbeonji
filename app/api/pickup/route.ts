import { pickupConfiguration, submitPickup } from '@/lib/pickup';
import { dashboardConfiguration, submitDashboardPickup } from '@/lib/dashboard-pickup';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';

export const dynamic = 'force-dynamic';

export function GET() {
  const settings = serverSettings();

  return Response.json({
    debug: true,
    deliveryMode: settings.PICKUP_DELIVERY_MODE ?? null,
    hasDb: !!pickupDb(),
    hasAdminPassword: !!settings.ADMIN_PASSWORD,
    hasAdminSessionSecret: !!settings.ADMIN_SESSION_SECRET,
    hasSiteOrigin: !!settings.SITE_ORIGIN,
    hasTurnstileSiteKey: !!settings.TURNSTILE_SITE_KEY,
    hasTurnstileSecretKey: !!settings.TURNSTILE_SECRET_KEY,
  });
}

export async function POST(request: Request) {
  const settings = serverSettings();
  if (settings.PICKUP_DELIVERY_MODE === 'dashboard')
    return submitDashboardPickup(request, settings, pickupDb());
  return submitPickup(request, settings);
}
