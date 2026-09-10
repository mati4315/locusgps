import { Router } from 'express';
import { pool } from '../db/pool.js';

export const mapPointsRouter = Router();
const allowedTypes = new Set(['favorite', 'camera', 'speed_camera', 'traffic_light_camera', 'danger', 'school_zone', 'fuel', 'parking', 'rest_area', 'custom']);

function coordinate(value, min, max) {
  const number = Number(value);
  return Number.isFinite(number) && number >= min && number <= max ? number : null;
}

function pointPayload(body) {
  const type = typeof body?.type === 'string' ? body.type.trim() : '';
  const name = typeof body?.name === 'string' ? body.name.trim() : '';
  const latitude = coordinate(body?.latitude, -90, 90);
  const longitude = coordinate(body?.longitude, -180, 180);
  const description = typeof body?.description === 'string' ? body.description.trim().slice(0, 500) : null;
  const enabled = body?.enabled === undefined ? true : body.enabled;
  const alertEnabled = body?.alert_enabled === undefined ? true : body.alert_enabled;
  const direction = body?.direction == null ? null : coordinate(body.direction, 0, 359);
  if (!allowedTypes.has(type) || !name || name.length > 120 || latitude == null || longitude == null || typeof enabled !== 'boolean' || typeof alertEnabled !== 'boolean' || (direction != null && !Number.isInteger(direction))) return null;
  return { type, name, latitude, longitude, description, enabled, alertEnabled, direction };
}

mapPointsRouter.get('/api/map-points', async (req, res, next) => {
  try {
    const latitude = coordinate(req.query.lat, -90, 90);
    const longitude = coordinate(req.query.lon, -180, 180);
    const radius = Math.min(Math.max(Number(req.query.radius ?? 1000), 1), 50_000);
    if (latitude == null || longitude == null || !Number.isFinite(radius)) return res.status(400).json({ error: 'invalid_area' });
    const type = typeof req.query.type === 'string' && allowedTypes.has(req.query.type) ? req.query.type : null;
    const latitudeDelta = radius / 111_320;
    const longitudeDelta = radius / (111_320 * Math.max(Math.cos(latitude * Math.PI / 180), 0.1));
    const [rows] = await pool.execute(
      `SELECT id, type, name, latitude, longitude, description, enabled, alert_enabled AS alertEnabled, direction, source, verified_at AS verifiedAt
       FROM map_points WHERE user_id = ? AND enabled = TRUE AND latitude BETWEEN ? AND ? AND longitude BETWEEN ? AND ? ${type ? 'AND type = ?' : ''}
       ORDER BY id DESC LIMIT 500`,
      type ? [req.device.userId, latitude - latitudeDelta, latitude + latitudeDelta, longitude - longitudeDelta, longitude + longitudeDelta, type] : [req.device.userId, latitude - latitudeDelta, latitude + latitudeDelta, longitude - longitudeDelta, longitude + longitudeDelta],
    );
    res.json(rows);
  } catch (error) { next(error); }
});

mapPointsRouter.post('/api/map-points', async (req, res, next) => {
  try {
    const point = pointPayload(req.body);
    if (!point) return res.status(400).json({ error: 'invalid_map_point' });
    const [result] = await pool.execute(
      `INSERT INTO map_points (user_id, type, name, latitude, longitude, description, enabled, alert_enabled, direction)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
      [req.device.userId, point.type, point.name, point.latitude, point.longitude, point.description, point.enabled, point.alertEnabled, point.direction],
    );
    res.status(201).json({ id: result.insertId, ...point });
  } catch (error) { next(error); }
});

mapPointsRouter.patch('/api/map-points/:id', async (req, res, next) => {
  try {
    const point = pointPayload(req.body);
    const id = Number(req.params.id);
    if (!point || !Number.isSafeInteger(id) || id <= 0) return res.status(400).json({ error: 'invalid_map_point' });
    const [result] = await pool.execute(
      `UPDATE map_points SET type = ?, name = ?, latitude = ?, longitude = ?, description = ?, enabled = ?, alert_enabled = ?, direction = ? WHERE id = ? AND user_id = ?`,
      [point.type, point.name, point.latitude, point.longitude, point.description, point.enabled, point.alertEnabled, point.direction, id, req.device.userId],
    );
    if (!result.affectedRows) return res.status(404).json({ error: 'not_found' });
    res.json({ id, ...point });
  } catch (error) { next(error); }
});

mapPointsRouter.delete('/api/map-points/:id', async (req, res, next) => {
  try {
    const id = Number(req.params.id);
    if (!Number.isSafeInteger(id) || id <= 0) return res.status(400).json({ error: 'invalid_id' });
    const [result] = await pool.execute('DELETE FROM map_points WHERE id = ? AND user_id = ?', [id, req.device.userId]);
    if (!result.affectedRows) return res.status(404).json({ error: 'not_found' });
    res.sendStatus(204);
  } catch (error) { next(error); }
});
