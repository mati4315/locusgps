import { Router } from 'express';
import { pool } from '../db/pool.js';

export const settingsRouter = Router();
const modes = new Set(['driving', 'cycling', 'walking']);
const units = new Set(['metric', 'imperial']);

settingsRouter.get('/api/settings', async (req, res, next) => {
  try {
    const [rows] = await pool.execute(
      `SELECT voice_enabled AS voiceEnabled, transport_mode AS transportMode, default_radius AS defaultRadius, units
       FROM settings WHERE user_id = ? LIMIT 1`,
      [req.device.userId],
    );
    res.json(rows[0] ?? { voiceEnabled: true, transportMode: 'driving', defaultRadius: 500, units: 'metric' });
  } catch (error) { next(error); }
});

settingsRouter.patch('/api/settings', async (req, res, next) => {
  try {
    const voiceEnabled = req.body?.voiceEnabled;
    const transportMode = req.body?.transportMode;
    const defaultRadius = Number(req.body?.defaultRadius);
    const selectedUnits = req.body?.units;
    if (typeof voiceEnabled !== 'boolean' || !modes.has(transportMode) || !Number.isInteger(defaultRadius) || defaultRadius < 50 || defaultRadius > 5000 || !units.has(selectedUnits)) {
      return res.status(400).json({ error: 'invalid_settings' });
    }
    await pool.execute(
      `INSERT INTO settings (user_id, voice_enabled, transport_mode, default_radius, units)
       VALUES (?, ?, ?, ?, ?)
       ON DUPLICATE KEY UPDATE voice_enabled = VALUES(voice_enabled), transport_mode = VALUES(transport_mode), default_radius = VALUES(default_radius), units = VALUES(units)`,
      [req.device.userId, voiceEnabled, transportMode, defaultRadius, selectedUnits],
    );
    res.json({ voiceEnabled, transportMode, defaultRadius, units: selectedUnits });
  } catch (error) { next(error); }
});
