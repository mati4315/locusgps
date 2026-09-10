import 'dotenv/config';

function required(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing required environment variable: ${name}`);
  return value;
}

export const env = {
  nodeEnv: process.env.NODE_ENV ?? 'development',
  port: Number(process.env.PORT ?? 3000),
  db: {
    host: required('DB_HOST'),
    port: Number(process.env.DB_PORT ?? 3306),
    name: required('DB_NAME'),
    user: required('DB_USER'),
    password: required('DB_PASSWORD'),
    connectionLimit: Number(process.env.DB_CONNECTION_LIMIT ?? 3),
  },
  deviceTokenHash: process.env.DEVICE_TOKEN_HASH ?? '',
  graphhopper: {
    apiKey: process.env.GRAPHHOPPER_API_KEY ?? '',
    baseUrl: (process.env.GRAPHHOPPER_BASE_URL ?? 'https://graphhopper.com/api/1').replace(/\/$/, ''),
  },
  corsOrigins: (process.env.CORS_ORIGINS ?? '')
    .split(',')
    .map((origin) => origin.trim())
    .filter(Boolean),
};
