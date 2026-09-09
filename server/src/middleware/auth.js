import crypto from 'node:crypto';
import { env } from '../config/env.js';

export function authenticateDevice(req, res, next) {
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

  req.device = { tokenHash: actual };
  next();
}
