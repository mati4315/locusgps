# Locus GPS

MVP Android de un navegador GPS personal. La primera entrega implementa el mapa, permisos de ubicación y la posición local; no envía lecturas GPS a ningún servidor.

## Abrir y ejecutar

1. Abre esta carpeta con Android Studio (JDK 17 y Android SDK 35).
2. Copia `maptiler.properties.example` como `maptiler.properties` en la raíz y reemplaza el valor de `MAPTILER_KEY`. El archivo real está ignorado por Git.
3. Sincroniza Gradle y ejecuta en un dispositivo Android con ubicación habilitada.

También se puede compilar desde Windows sin Android Studio:

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK resultante queda en `app/build/outputs/apk/debug/app-debug.apk`.

La clave se inyecta únicamente en el `BuildConfig` de la app. Antes de una distribución pública, debe restringirse en el panel de MapTiler al identificador de paquete y certificado de firma.

## Estado de fases

- [x] Fase 1: proyecto Android, MapLibre, MapTiler configurable, permisos y seguimiento local de la posición.
- [x] Fase 2: backend Hostinger, autenticación Bearer, migraciones MySQL, ubicación actual y favoritos.
- [ ] Fase 3: rutas mediante `routingProvider` / GraphHopper.
- [ ] Fase 4: navegación, maniobras, voz y recálculo.
- [ ] Fase 5: `map_points`, favoritos y alertas locales.
- [ ] Fase 6: búsqueda y lugares.
- [ ] Fase 7: IA opcional.

## Arquitectura local actual

`MainActivity` contiene la UI. `LocationRepository` obtiene ubicación con `LocationManager`, y `MapScreen` adapta `MapView` de MapLibre a Compose. La cámara se actualiza localmente y la clave de tiles está centralizada en `MapStyleConfig`.

## Backend local

La base del backend está en `server/`. Copia `server/.env.example` a `server/.env`, instala dependencias con `npm install` y ejecuta `npm run start`. Antes de usar datos persistentes, crea la base de datos MySQL indicada en `.env` y ejecuta `npm run migrate`. `GET /health` y `GET /` son públicos; ubicación y favoritos requieren `Authorization: Bearer <token>`. En Hostinger se deben configurar las variables de base de datos y `DEVICE_TOKEN_HASH` (SHA-256 del token privado, nunca el token en el repositorio). En la primera petición autenticada se crea automáticamente el usuario personal y el dispositivo.

La app Android usa `https://locusgps.pro` como API por defecto y muestra el estado de `/health` en la pantalla principal. Se puede cambiar en compilación con `-PAPI_BASE_URL=https://api.locusgps.pro` cuando el subdominio esté conectado.
