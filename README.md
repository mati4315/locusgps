# Locus GPS

Navegador GPS personal para Android, optimizado para privacidad, bajo consumo y navegación local.

La aplicación usa Android + MapLibre + MapTiler/OSM. Hostinger ejecuta una API Node.js/Express y MySQL. GraphHopper se consume únicamente desde el backend mediante `routingProvider`; la clave nunca se incluye en Android.

## Funcionalidad actual

- Mapa MapLibre con estilo MapTiler.
- Permisos y seguimiento GPS local.
- Búsqueda de lugares mediante MapTiler Geocoding.
- Rutas GraphHopper con geometría, distancia, ETA e instrucciones.
- Dibujo de rutas y progreso local.
- Detección de salida de ruta y recálculo con umbral.
- Voz opcional para inicio y próximas maniobras.
- Puntos personalizados, cámaras, peligros y favoritos.
- Alertas locales de proximidad y filtros de capas.
- Autenticación Bearer y persistencia MySQL.
- No se envía GPS continuamente al servidor.

## Estructura

```text
app/       Aplicación Android Kotlin/Compose/MapLibre
server/    API Node.js/Express/MySQL
guia/      Documentación de arquitectura del proyecto
```

## Android

Requisitos: JDK 17 y Android SDK 35.

1. Copia `maptiler.properties.example` a `maptiler.properties` y define `MAPTILER_KEY`.
2. Copia `api.properties.example` a `api.properties` y define `API_TOKEN`.
3. Sincroniza Gradle y ejecuta en un dispositivo Android con ubicación habilitada.

Ambos archivos están ignorados por Git y nunca deben publicarse.

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

La API por defecto es `https://locusgps.pro`. Puede cambiarse con `-PAPI_BASE_URL=https://api.locusgps.pro`.

## Backend

```powershell
cd server
npm install
Copy-Item .env.example .env
npm run migrate
npm run start
```

Variables necesarias en `.env` o en Hostinger:

```text
NODE_ENV=production
DB_HOST=localhost
DB_PORT=3306
DB_NAME=...
DB_USER=...
DB_PASSWORD=...
DEVICE_TOKEN_HASH=sha256-del-token-privado
GRAPHHOPPER_API_KEY=...
GRAPHHOPPER_BASE_URL=https://graphhopper.com/api/1
MAPTILER_API_KEY=...
```

No guardar contraseñas, tokens ni claves en GitHub. `DEVICE_TOKEN_HASH` es el SHA-256 del token Bearer que usa Android; el token original nunca se almacena en el servidor.

## API principal

Públicos: `GET /health` y `GET /`.

Requieren `Authorization: Bearer <token>`:

```text
POST   /api/routes
GET    /api/search?q=...
GET    /api/location
POST   /api/location
GET    /api/favorites
POST   /api/favorites
DELETE /api/favorites/:id
GET    /api/map-points?lat=&lon=&radius=&type=
POST   /api/map-points
PATCH  /api/map-points/:id
DELETE /api/map-points/:id
```

Después de desplegar cambios de base de datos en Hostinger, ejecutar `npm run migrate` para aplicar las migraciones, incluida `002_map_points.sql`.

## Principios de arquitectura

- Android obtiene GPS, dibuja el mapa, sigue la ruta y calcula proximidad localmente.
- El servidor coordina autenticación, búsqueda, routing y sincronización puntual.
- MySQL almacena solo lo necesario: usuarios, dispositivos, configuración, ubicación actual, favoritos y puntos personalizados.
- No se guarda historial GPS ni se envía una lectura por segundo.
- La IA es opcional y no controla navegación, GPS ni alertas críticas.

## Estado de desarrollo

- [x] Fase 1: mapa, MapTiler/OSM, permisos y posición local.
- [x] Fase 2: Hostinger, Node.js, MySQL y autenticación.
- [x] Fase 3: `routingProvider` con GraphHopper.
- [x] Fase 4: navegación, ETA, voz y recálculo.
- [x] Fase 5: puntos personalizados, capas y alertas locales.
- [x] Fase 6: búsqueda y selección de destinos.
- [ ] Fase 7: IA opcional.
