import { Router } from 'express';
import { searchPlaces } from '../providers/maptiler.js';

export const searchRouter = Router();

searchRouter.get('/api/search', async (req, res, next) => {
  try {
    const latitude = Number(req.query.lat);
    const longitude = Number(req.query.lon);
    const results = await searchPlaces(req.query.q, { latitude, longitude });
    res.json({ results });
  } catch (error) {
    next(error);
  }
});
