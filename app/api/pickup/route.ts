import { pickupConfiguration, submitPickup } from '@/lib/pickup';
import { dashboardConfiguration, submitDashboardPickup } from '@/lib/dashboard-pickup';
import { pickupDb, serverSettings } from '@/lib/admin-runtime';

export const dynamic = 'force-dynamic';

export function GET() {
  const settings = serverSettings();
  if (settings.PICKUP_DELIVERY_MODE === 'dashboard')
    return dashboardConfiguration(settings, pickupDb());
  return pickupConfiguration(settings);
}

export async function POST(request: Request) {
  const settings = serverSettings();
  if (settings.PICKUP_DELIVERY_MODE === 'dashboard')
    return submitDashboardPickup(request, settings, pickupDb());
  return submitPickup(request, settings);
}
