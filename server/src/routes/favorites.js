import { Router } from 'express';
import { pool } from '../db/pool.js';

export const favoritesRouter = Router();

function coordinate(value, min, max) {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
}

favoritesRouter.get('/api/favorites', async (req, res, next) => {
  try {
    const [rows] = await pool.execute(
      `SELECT id, name, latitude, longitude, address, created_at AS createdAt
       FROM favorites WHERE user_id = ? ORDER BY name`,
      [req.device.userId],
    );
    res.json(rows);
  } catch (error) {
    next(error);
  }
});

favoritesRouter.post('/api/favorites', async (req, res, next) => {
  try {
    const name = typeof req.body?.name === 'string' ? req.body.name.trim() : '';
    const latitude = coordinate(req.body?.latitude, -90, 90);
    const longitude = coordinate(req.body?.longitude, -180, 180);
    const address = typeof req.body?.address === 'string' ? req.body.address.trim().slice(0, 255) : null;
    if (!name || name.length > 120 || latitude == null || longitude == null) {
      return res.status(400).json({ error: 'invalid_favorite' });
    }
    const [result] = await pool.execute(
      'INSERT INTO favorites (user_id, name, latitude, longitude, address) VALUES (?, ?, ?, ?, ?)',
      [req.device.userId, name, latitude, longitude, address],
    );
    res.status(201).json({ id: result.insertId, name, latitude, longitude, address });
  } catch (error) {
    next(error);
  }
});

favoritesRouter.delete('/api/favorites/:id', async (req, res, next) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id <= 0) return res.status(400).json({ error: 'invalid_id' });
    const [result] = await pool.execute('DELETE FROM favorites WHERE id = ? AND user_id = ?', [id, req.device.userId]);
    if (result.affectedRows === 0) return res.status(404).json({ error: 'not_found' });
    res.sendStatus(204);
  } catch (error) {
    next(error);
  }
});
