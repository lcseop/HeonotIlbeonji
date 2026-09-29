import { pickupConfiguration, submitPickup } from '@/lib/pickup';

export const dynamic = 'force-dynamic';

export function GET() {
  return pickupConfiguration(process.env);
}

export async function POST(request: Request) {
  return submitPickup(request, process.env);
}
