const windows = new Map();

export function rateLimit({ windowMs = 60_000, max = 60 } = {}) {
  return (req, res, next) => {
    const key = req.ip ?? 'unknown';
    const now = Date.now();
    const current = windows.get(key);
    if (!current || current.expiresAt <= now) {
      windows.set(key, { count: 1, expiresAt: now + windowMs });
      return next();
    }
    current.count += 1;
    if (current.count > max) {
      return res.status(429).json({ error: 'rate_limited' });
    }
    next();
  };
}
