export function notFound(req, res) {
  res.status(404).json({ error: 'not_found' });
}

export function errorHandler(error, req, res, next) { // eslint-disable-line no-unused-vars
  console.error(`${req.method} ${req.path}`, error.message);
  res.status(500).json({ error: 'internal_error' });
}
