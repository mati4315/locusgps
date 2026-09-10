import { Router } from 'express';
import { calculateRoute } from '../providers/graphhopper.js';

export const routesRouter = Router();

routesRouter.post('/api/routes', async (req, res, next) => {
  try {
    const { origin, destination, mode } = req.body ?? {};
    const route = await calculateRoute({ origin, destination, mode });
    res.json(route);
  } catch (error) {
    next(error);
  }
});
