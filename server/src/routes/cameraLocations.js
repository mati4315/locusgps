import { Router } from 'express';

// Fuente oficial enlazada por Queensland TMR/QPS.
// Revisada: 31-08-2026. No incluye cámaras móviles.
const GOLD_COAST_CAMERAS = [
  ['Fixed speed camera · Gold Coast Highway, Labrador', 'speed_camera', -27.956300, 153.409440],
  ['Fixed speed camera · Gold Coast Highway, Broadbeach', 'speed_camera', -28.024450, 153.428740],
  ['Combined red light/speed · Gold Coast Hwy / Margaret Ave, Broadbeach', 'traffic_light_camera', -28.033842, 153.431218],
  ['Red light · Southport-Nerang Rd / Ashmore Rd, Ashmore', 'traffic_light_camera', -27.985216, 153.365597],
  ['Red light · Gold Coast Hwy / Ada Bell Way, Southport', 'traffic_light_camera', -27.971921, 153.419828],
  ['Red light · Gold Coast Hwy / Stewart Rd, Tugun', 'traffic_light_camera', -28.141641, 153.492126],
  ['Combined red light/speed · Gold Coast Hwy / Olsen Ave, Labrador', 'traffic_light_camera', -27.934703, 153.390802],
  ['Red light · Musgrave St / Coolangatta Rd, Bilinga', 'traffic_light_camera', -28.167149, 153.521974],
  ['Red light · North St / Scarborough St, Southport', 'traffic_light_camera', -27.961502, 153.409642],
  ['Combined red light/speed · Kumbari Ave / Smith St, Southport', 'traffic_light_camera', -27.961531, 153.395240],
  ['Combined red light/speed · Bermuda St / Rudd St, Broadbeach Waters', 'traffic_light_camera', -28.029004, 153.410200],
  ['Combined red light/speed · Southport-Nerang Rd / Currumburra Rd, Ashmore', 'traffic_light_camera', -27.976805, 153.381374],
  ['Red light · Gold Coast Hwy / Government Rd, Labrador', 'traffic_light_camera', -27.935758, 153.401421],
  ['Red light · Olsen Ave / Napper Rd, Arundel', 'traffic_light_camera', -27.954299, 153.383429],
  ['Red light · Government Rd / Central St, Labrador', 'traffic_light_camera', -27.948156, 153.398009],
  ['Red light · Wardoo St / Queen St, Southport', 'traffic_light_camera', -27.969871, 153.394019],
  ['Red light · Bermuda St / Cottesloe Dr, Mermaid Waters', 'traffic_light_camera', -28.064913, 153.418548],
  ['Red light · Townson Ave / Nineteenth Ave, Palm Beach', 'traffic_light_camera', -28.111389, 153.462776],
  ['Combined red light/speed · Bermuda St / Christine Ave, Burleigh Waters', 'traffic_light_camera', -28.086238, 153.425907],
  ['Combined red light/speed · Markeri St / Bermuda St, Clear Island Waters', 'traffic_light_camera', -28.048497, 153.406601],
  ['Red light · Turpin Rd / Central St, Labrador', 'traffic_light_camera', -27.948791, 153.402038],
  ['Red light · Gold Coast Hwy / Discovery Dr, Helensvale', 'traffic_light_camera', -27.922710, 153.337713],
  ['Combined red light/speed · Nerang-Broadbeach Rd / Southport-Burleigh Rd', 'traffic_light_camera', -28.035528, 153.409137],
  ['Point-to-point speed · Pacific Motorway M1, Gaven/Arundel', 'speed_camera', -27.950570, 153.342140],
  ['Point-to-point speed · Pacific Motorway M1, Oxenford/Helensvale', 'speed_camera', -27.884610, 153.315310],
];

const router = Router();
const toRadians = (value) => value * Math.PI / 180;
const distanceMeters = (aLat, aLon, bLat, bLon) => {
  const earthRadius = 6371000;
  const dLat = toRadians(bLat - aLat);
  const dLon = toRadians(bLon - aLon);
  const lat1 = toRadians(aLat);
  const lat2 = toRadians(bLat);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
  return 2 * earthRadius * Math.asin(Math.sqrt(h));
};

router.get('/api/camera-locations', (req, res) => {
  const lat = Number(req.query.lat);
  const lon = Number(req.query.lon);
  const radius = Math.min(Math.max(Number(req.query.radius) || 30000, 1000), 50000);
  const hasOrigin = Number.isFinite(lat) && Number.isFinite(lon) && lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180;
  const results = GOLD_COAST_CAMERAS
    .map(([name, type, latitude, longitude], index) => ({
      id: -(index + 1),
      type,
      name: `QLD oficial · ${name}`,
      latitude,
      longitude,
      alertEnabled: true,
      source: 'qld-tmr-qps',
      verifiedAt: '2026-08-31',
      distanceMeters: hasOrigin ? Math.round(distanceMeters(lat, lon, latitude, longitude)) : null,
    }))
    .filter((point) => !hasOrigin || point.distanceMeters <= radius);
  res.json({ source: 'Queensland TMR/QPS', verifiedAt: '2026-08-31', results });
});

export { router as cameraLocationsRouter };
