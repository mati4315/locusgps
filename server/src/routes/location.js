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
    await pool.execute(
      `INSERT INTO current_location (device_id, latitude, longitude, accuracy, recorded_at)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE latitude = VALUES(latitude), longitude = VALUES(longitude),
       accuracy = VALUES(accuracy), recorded_at = VALUES(recorded_at)`,
      [req.device.deviceId, latitude, longitude, accuracy, recordedAt],
    );
    res.status(202).json({ accepted: true, stored: true });
  } catch (error) {
    next(error);
  }
});

locationRouter.get('/api/location', async (req, res, next) => {
  try {
    const [rows] = await pool.execute(
      `SELECT latitude, longitude, accuracy, recorded_at AS recordedAt, updated_at AS updatedAt
       FROM current_location WHERE device_id = ? LIMIT 1`,
      [req.device.deviceId],
    );
    res.json(rows[0] ?? null);
  } catch (error) {
    next(error);
  }
});
