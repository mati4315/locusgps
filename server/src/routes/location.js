import { Router } from 'express';
import { pool } from '../db/pool.js';

export const locationRouter = Router();

function coordinate(value, min, max) {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
}

locationRouter.post('/api/location', async (req, res, next) => {
  try {
    const latitude = coordinate(req.body?.latitude, -90, 90);
    const longitude = coordinate(req.body?.longitude, -180, 180);
    const accuracy = req.body?.accuracy == null ? null : coordinate(req.body.accuracy, 0, 100000);
    const recordedAt = req.body?.timestamp ? new Date(req.body.timestamp) : new Date();
    if (latitude == null || longitude == null || (accuracy == null && req.body?.accuracy != null) || Number.isNaN(recordedAt.getTime())) {
      return res.status(400).json({ error: 'invalid_location' });
    }
    // Device resolution is deliberately deferred until device registration is added.
    res.status(202).json({ accepted: true, stored: false });
  } catch (error) {
    next(error);
  }
});

locationRouter.get('/api/location', (req, res) => {
  res.status(501).json({ error: 'device_registration_required' });
});
