export function notFound(req, res) {
  res.status(404).json({ error: 'not_found' });
}

export function errorHandler(error, req, res, next) { // eslint-disable-line no-unused-vars
  console.error(`${req.method} ${req.path}`, error.message);
  const status = Number.isInteger(error.statusCode) ? error.statusCode : 500;
  res.status(status).json({ error: status >= 500 ? 'internal_error' : error.message });
}
