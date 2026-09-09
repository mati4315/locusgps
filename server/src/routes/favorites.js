import { Router } from 'express';

export const favoritesRouter = Router();

favoritesRouter.get('/api/favorites', (req, res) => res.json([]));
favoritesRouter.post('/api/favorites', (req, res) => res.status(501).json({ error: 'user_binding_required' }));
favoritesRouter.delete('/api/favorites/:id', (req, res) => res.status(501).json({ error: 'user_binding_required' }));
