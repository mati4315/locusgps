import { env } from '../config/env.js';

export async function searchPlaces(query, { latitude, longitude } = {}) {
  if (!env.maptilerApiKey) throw Object.assign(new Error('search_not_configured'), { statusCode: 503 });
  const text = String(query ?? '').trim();
  if (text.length < 2 || text.length > 120) throw Object.assign(new Error('invalid_query'), { statusCode: 400 });
  const params = new URLSearchParams({ key: env.maptilerApiKey, language: 'es', limit: '5' });
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) params.set('proximity', `${longitude},${latitude}`);
  const response = await fetch(`https://api.maptiler.com/geocoding/${encodeURIComponent(text)}.json?${params}`, { signal: AbortSignal.timeout(10_000) });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw Object.assign(new Error(body.message || `search_provider_http_${response.status}`), { statusCode: response.status >= 500 ? 502 : 400 });
  return (body.features ?? []).map((feature) => ({
    id: feature.id,
    name: feature.text || feature.place_name || '',
    address: feature.place_name || feature.text || '',
    latitude: feature.geometry?.coordinates?.[1],
    longitude: feature.geometry?.coordinates?.[0],
    type: feature.properties?.kind || feature.place_type?.[0] || 'place',
  })).filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude));
}
