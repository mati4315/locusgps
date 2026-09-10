import { env } from '../config/env.js';

const profiles = { driving: 'car', car: 'car', cycling: 'bike', walking: 'foot' };

function point(value, name) {
  const latitude = Number(value?.latitude);
  const longitude = Number(value?.longitude);
  if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
    throw Object.assign(new Error(`invalid_${name}`), { statusCode: 400 });
  }
  return { latitude, longitude };
}

export async function calculateRoute({ origin, destination, mode = 'driving' }) {
  if (!env.graphhopper.apiKey) throw Object.assign(new Error('routing_not_configured'), { statusCode: 503 });
  const start = point(origin, 'origin');
  const end = point(destination, 'destination');
  const profile = profiles[mode];
  if (!profile) throw Object.assign(new Error('invalid_mode'), { statusCode: 400 });

  const params = new URLSearchParams({
    profile,
    locale: 'es',
    instructions: 'true',
    calc_points: 'true',
    points_encoded: 'false',
    key: env.graphhopper.apiKey,
  });
  params.append('point', `${start.latitude},${start.longitude}`);
  params.append('point', `${end.latitude},${end.longitude}`);
  const response = await fetch(`${env.graphhopper.baseUrl}/route?${params}`, { signal: AbortSignal.timeout(15_000) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body.message || `routing_provider_http_${response.status}`);
    error.statusCode = response.status >= 500 ? 502 : 400;
    throw error;
  }
  const path = body.paths?.[0];
  if (!path) throw Object.assign(new Error('route_not_found'), { statusCode: 404 });
  return {
    provider: 'graphhopper',
    distanceMeters: path.distance,
    durationSeconds: Math.round(path.time / 1000),
    geometry: (path.points?.coordinates ?? []).map(([longitude, latitude]) => ({ latitude, longitude })),
    instructions: (path.instructions ?? []).map((instruction) => ({
      text: instruction.text,
      distanceMeters: instruction.distance,
      durationSeconds: Math.round((instruction.time ?? 0) / 1000),
      sign: instruction.sign,
      intervalStart: instruction.interval?.[0] ?? 0,
      intervalEnd: instruction.interval?.[1] ?? 0,
    })),
  };
}
