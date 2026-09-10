import { Router } from 'express';
import { searchPlaces } from '../providers/maptiler.js';

export const assistantRouter = Router();

function interpret(prompt) {
  const text = String(prompt ?? '').trim();
  if (text.length < 2 || text.length > 300) throw Object.assign(new Error('invalid_prompt'), { statusCode: 400 });
  const lower = text.toLocaleLowerCase('es');
  const type = lower.includes('gasolin') ? 'fuel' : lower.includes('parking') || lower.includes('estacionamiento') ? 'parking' : lower.includes('restaurante') || lower.includes('comida') ? 'restaurant' : 'place';
  const cleaned = text.replace(/\b(busca|quiero|necesito|cerca|cercano|cercana|por favor|un|una|el|la|me)\b/gi, ' ').replace(/\s+/g, ' ').trim();
  return { query: cleaned || text, type };
}

assistantRouter.post('/api/assistant/search', async (req, res, next) => {
  try {
    const criteria = interpret(req.body?.prompt);
    const latitude = Number(req.body?.lat);
    const longitude = Number(req.body?.lon);
    const results = await searchPlaces(criteria.query, { latitude, longitude });
    res.json({ mode: 'local-fallback', criteria, results });
  } catch (error) { next(error); }
});
