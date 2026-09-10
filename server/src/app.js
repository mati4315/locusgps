import express from 'express';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import helmet from 'helmet';
import { env } from './config/env.js';
import { authenticateDevice } from './middleware/auth.js';
import { rateLimit } from './middleware/rateLimit.js';
import { errorHandler, notFound } from './middleware/errors.js';
import { healthRouter } from './routes/health.js';
import { locationRouter } from './routes/location.js';
import { favoritesRouter } from './routes/favorites.js';
import { routesRouter } from './routes/routes.js';
import { searchRouter } from './routes/search.js';
import { mapPointsRouter } from './routes/mapPoints.js';
import { settingsRouter } from './routes/settings.js';
import { assistantRouter } from './routes/assistant.js';
import { cameraLocationsRouter } from './routes/cameraLocations.js';

const app = express();
const publicDirectory = path.join(path.dirname(fileURLToPath(import.meta.url)), '../public');
app.disable('x-powered-by');
app.use(helmet());
// Android App Links necesita acceder a /.well-known/assetlinks.json.
app.use(express.static(publicDirectory, { index: false, maxAge: '1d', dotfiles: 'allow' }));
app.use(rateLimit({ windowMs: 60_000, max: 120 }));
app.use(express.json({ limit: '32kb' }));
app.use((req, res, next) => {
  const origin = req.get('origin');
  if (origin && env.corsOrigins.includes(origin)) {
    res.setHeader('Access-Control-Allow-Origin', origin);
    res.setHeader('Vary', 'Origin');
  }
  if (req.method === 'OPTIONS') {
    res.setHeader('Access-Control-Allow-Methods', 'GET,POST,DELETE,OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
    return res.sendStatus(204);
  }
  next();
});

app.use(healthRouter);
app.use(cameraLocationsRouter);
app.get('/', (req, res) => {
  res.json({
    service: 'locus-gps-api',
    status: 'running',
    health: '/health',
  });
});
app.use(authenticateDevice);
app.use(searchRouter);
app.use(mapPointsRouter);
app.use(settingsRouter);
app.use(assistantRouter);
app.use(locationRouter);
app.use(favoritesRouter);
app.use(routesRouter);
app.use(notFound);
app.use(errorHandler);

app.listen(env.port, () => {
  console.log(`Locus GPS API listening on port ${env.port}`);
});
