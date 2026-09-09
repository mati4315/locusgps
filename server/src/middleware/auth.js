import crypto from 'node:crypto';
import { env } from '../config/env.js';
import { pool } from '../db/pool.js';

export async function authenticateDevice(req, res, next) {
  const header = req.get('authorization') ?? '';
  const [scheme, token] = header.split(' ');
  if (scheme !== 'Bearer' || !token || !env.deviceTokenHash) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const actual = crypto.createHash('sha256').update(token, 'utf8').digest('hex');
  const expected = env.deviceTokenHash.toLowerCase();
  const valid = actual.length === expected.length && crypto.timingSafeEqual(
    Buffer.from(actual),
    Buffer.from(expected),
  );
  if (!valid) return res.status(401).json({ error: 'unauthorized' });

  try {
    const [users] = await pool.query('SELECT id FROM users ORDER BY id LIMIT 1');
    let userId = users[0]?.id;
    if (!userId) {
      const [result] = await pool.execute('INSERT INTO users (username) VALUES (?)', ['personal']);
      userId = result.insertId;
      await pool.execute('INSERT INTO settings (user_id) VALUES (?)', [userId]);
    }
    await pool.execute(
      `INSERT INTO devices (user_id, device_name, token_hash, active, last_seen_at)
       VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
       ON DUPLICATE KEY UPDATE user_id = VALUES(user_id), active = TRUE, last_seen_at = CURRENT_TIMESTAMP`,
      [userId, 'Android principal', actual],
    );
    const [devices] = await pool.execute('SELECT id FROM devices WHERE token_hash = ? AND active = TRUE LIMIT 1', [actual]);
    req.device = { deviceId: devices[0].id, userId, tokenHash: actual };
    next();
  } catch (error) {
    next(error);
  }
}
