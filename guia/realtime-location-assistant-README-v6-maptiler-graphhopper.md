# Navegador GPS personal con ubicación en tiempo real

## Decisiones cerradas para la V1

La primera versión adopta estas decisiones de infraestructura y cartografía:

- **Motor de mapa Android:** MapLibre Native.
- **Proveedor de tiles:** MapTiler.
- **Datos cartográficos:** basados en OpenStreetMap (OSM).
- **Routing:** GraphHopper como proveedor gestionado para la V1.
- **Abstracción de routing:** Android no dependerá directamente de GraphHopper; el backend expondrá `/api/routes` y utilizará un `routingProvider`.
- **Backend:** Node.js + Express en Hostinger.
- **Base de datos:** MySQL.

### Flujo cartográfico

```text
Android
  ↓
MapLibre Native
  ↓
MapTiler
  ↓
Datos basados en OpenStreetMap
```

MapLibre será únicamente el motor de visualización. El proveedor de tiles queda desacoplado para poder sustituir MapTiler en el futuro sin rediseñar la aplicación.

### Flujo de routing

```text
Android
  ↓
POST /api/routes
  ↓
Hostinger / Node.js
  ↓
routingProvider
  ↓
GraphHopper
  ↓
Ruta + geometría + instrucciones
```

El cliente Android no conocerá la implementación concreta del proveedor de routing. Esto permitirá cambiar GraphHopper por otro motor compatible en el futuro.

### Criterio de V1

No se utilizarán los servidores públicos de OSM ni un servidor público de demostración de OSRM como dependencia crítica de producción. La aplicación debe poder seguir mostrando el mapa y, dentro de lo posible, continuar la navegación con los datos ya descargados/caché si una API externa no responde.


## Arquitectura optimizada para Hostinger + Node.js + MySQL

## 1. Objetivo

Este proyecto será principalmente un **navegador GPS personal para Android**.

La navegación es la función principal. La búsqueda de lugares forma parte del navegador y la IA será opcional.

La arquitectura está diseñada específicamente para:

- utilizar el hosting administrado de Hostinger;
- ejecutar una API Node.js + Express;
- utilizar MySQL como base de datos principal;
- evitar depender de un VPS;
- minimizar consumo de CPU, RAM, almacenamiento y consultas;
- mantener la aplicación sencilla porque será de uso personal y de un solo usuario;
- mantener los proveedores de mapas y routing intercambiables;
- evitar que la IA forme parte del camino crítico de navegación.

La idea es no construir un servidor de mapas ni un motor de routing propio dentro de Hostinger.

---

# 2. Principio fundamental de optimización

El servidor no debe hacer trabajo que el teléfono pueda hacer mejor.

### El teléfono se encarga de:

- obtener GPS;
- mostrar el mapa;
- mostrar la posición actual;
- mantener la navegación activa;
- seguir el movimiento;
- calcular el progreso sobre la ruta;
- decidir cuándo hace falta recalcular;
- mostrar instrucciones;
- reproducir voz;
- gestionar la interfaz.

### Hostinger se encarga de:

- API HTTPS;
- autenticación;
- configuración del usuario/dispositivo;
- almacenar la última ubicación cuando sea necesario;
- búsqueda/geocodificación mediante proveedores externos;
- solicitar rutas a un proveedor de routing;
- devolver resultados al teléfono;
- guardar favoritos y preferencias;
- actuar como punto central de integración.

### MySQL se encarga de:

- configuración persistente;
- dispositivo autorizado;
- última ubicación;
- favoritos;
- preferencias;
- opcionalmente historial, si algún día realmente lo necesitamos.

No utilizaremos MySQL para almacenar cada lectura GPS.

---

# 3. Arquitectura definitiva

```text
                         INTERNET
                            │
                            ▼
                 ┌─────────────────────┐
                 │      HOSTINGER      │
                 │                     │
                 │ Node.js + Express   │
                 │ API HTTPS           │
                 └─────────┬───────────┘
                           │
             ┌─────────────┼──────────────┐
             │             │              │
             ▼             ▼              ▼
         ┌────────┐    ┌──────────┐   ┌──────────┐
         │ MySQL  │    │ Routing  │   │ Places / │
         │        │    │ Provider │   │ Geocoding│
         └────────┘    └──────────┘   └──────────┘
                           │
                           │
                    OSM + proveedor
                     compatible

                           ▲
                           │ HTTPS
                           │
                  ┌────────┴────────┐
                  │     ANDROID     │
                  │                 │
                  │ GPS             │
                  │ Mapa            │
                  │ Navegación      │
                  │ Voz             │
                  │ Rerouting       │
                  │ IA opcional     │
                  └─────────────────┘
```

La IA queda fuera del camino crítico:

```text
GPS → mapa → destino → ruta → navegación
                         │
                         └── IA opcional
```

---

# 4. Por qué esta arquitectura es adecuada para Hostinger

Para un proyecto personal de un solo usuario no necesitamos inicialmente:

- VPS;
- Docker;
- Kubernetes;
- Redis;
- PostgreSQL;
- PostGIS;
- servidor propio de Overpass;
- servidor propio de OSRM;
- servidor propio de mapas;
- microservicios;
- colas de mensajes;
- WebSockets;
- balanceadores;
- almacenamiento masivo.

Todo eso aumentaría la complejidad sin aportar una ventaja real para el MVP.

Hostinger será únicamente nuestra capa de backend.

El mapa y el routing seguirán siendo servicios externos.

---

# 5. Componentes

## A. Aplicación Android

La aplicación es el componente principal.

Responsabilidades:

```text
GPS
 ↓
posición actual
 ↓
mapa
 ↓
destino
 ↓
solicitud de ruta
 ↓
ruta
 ↓
navegación
 ↓
seguimiento GPS
 ↓
rerouting cuando sea necesario
```

La aplicación debe poder navegar aunque temporalmente no necesite comunicarse con la API.

No debemos enviar el GPS continuamente a Hostinger simplemente porque podemos hacerlo.

---

# 6. GPS y consumo de datos

La frecuencia de actualización dependerá del estado de la aplicación.

## Modo mapa sin navegación

El GPS puede utilizarse localmente y actualizar la interfaz según necesidad.

No es necesario enviar continuamente la posición al servidor.

## Modo navegación

El teléfono obtiene GPS con la frecuencia necesaria para navegar correctamente.

El servidor solo recibe información cuando exista una razón concreta:

- sincronizar la última ubicación;
- solicitar una ruta;
- recalcular una ruta;
- realizar una búsqueda;
- ejecutar una función que realmente necesite el backend.

## Regla

```text
GPS frecuente ≠ enviar GPS frecuente al servidor
```

Esto reduce:

- batería;
- datos móviles;
- consultas;
- escrituras MySQL;
- carga de Node.js.

---

# 7. MySQL: diseño mínimo y optimizado

Como el proyecto será personal, debemos evitar un modelo de datos empresarial.

La base inicial puede tener solamente estas tablas:

```text
users
devices
current_location
favorites
settings
```

No necesitamos una tabla de millones de posiciones.

---

# 8. Tabla users

Aunque exista un solo usuario, conviene mantener una estructura mínima.

```text
users
-------------------------
id
username
created_at
updated_at
```

Puede existir una sola fila.

Esto deja abierta la posibilidad de añadir usuarios en el futuro sin rediseñar toda la API.

---

# 9. Tabla devices

Permite autorizar el teléfono.

```text
devices
-------------------------
id
user_id
device_name
token_hash
created_at
last_seen_at
active
```

El token real nunca se almacena en texto plano.

La aplicación utiliza el token mediante:

```http
Authorization: Bearer TOKEN
```

El servidor compara de forma segura contra el hash almacenado.

---

# 10. Tabla current_location

Esta es una de las optimizaciones más importantes.

No guardaremos:

```text
GPS 10:00:01
GPS 10:00:02
GPS 10:00:03
GPS 10:00:04
...
```

Solo tendremos una ubicación actual por dispositivo.

```text
current_location
-------------------------
device_id
latitude
longitude
accuracy
recorded_at
updated_at
```

Cuando llega una actualización:

```text
UPDATE current_location
SET
    latitude = ?,
    longitude = ?,
    accuracy = ?,
    recorded_at = ?
WHERE device_id = ?
```

Si no existe:

```text
INSERT
```

Resultado:

```text
1 dispositivo
1 ubicación actual
1 fila
```

Esto es mucho más eficiente para nuestro caso.

---

# 11. No guardar historial inicialmente

No tendremos:

```text
location_history
```

en el MVP.

Esto reduce:

- almacenamiento;
- consultas;
- backups;
- crecimiento de la base;
- problemas de privacidad.

Si posteriormente queremos historial, se añadirá como una función independiente.

---

# 12. Tabla favorites

Los favoritos sí tienen sentido para un navegador personal.

Ejemplo:

```text
favorites
-------------------------
id
user_id
name
latitude
longitude
address
created_at
```

Ejemplos:

```text
Casa
Trabajo
Supermercado habitual
Restaurante favorito
```

No necesitamos almacenar información completa de los proveedores externos.

---

# 13. Tabla settings

Para preferencias personales:

```text
settings
-------------------------
user_id
voice_enabled
transport_mode
default_radius
units
updated_at
```

Ejemplo:

```text
transport_mode = driving
voice_enabled = true
units = metric
```

---

# 14. Lo que NO guardaremos en MySQL

No guardaremos inicialmente:

```text
❌ tiles del mapa
❌ datos completos de OpenStreetMap
❌ resultados completos de Overpass
❌ rutas completas permanentemente
❌ instrucciones de navegación permanentemente
❌ GPS cada segundo
❌ logs enormes
❌ respuestas completas de IA
❌ información duplicada de proveedores externos
```

La base de datos debe contener únicamente información que realmente pertenezca a nuestra aplicación.

---

# 15. API Node.js + Express

Estructura simplificada:

```text
server/
├── src/
│   ├── app.js
│   ├── config/
│   │   └── env.js
│   ├── middleware/
│   │   ├── auth.js
│   │   ├── rateLimit.js
│   │   └── errorHandler.js
│   ├── routes/
│   │   ├── health.js
│   │   ├── location.js
│   │   ├── places.js
│   │   ├── routes.js
│   │   ├── favorites.js
│   │   └── ai.js
│   ├── services/
│   │   ├── locationService.js
│   │   ├── placesService.js
│   │   ├── routingService.js
│   │   ├── navigationService.js
│   │   └── aiService.js
│   ├── providers/
│   │   ├── placesProvider.js
│   │   ├── routingProvider.js
│   │   └── osmProvider.js
│   └── db/
│       ├── pool.js
│       ├── migrations/
│       └── queries/
└── package.json
```

No necesitamos separar el proyecto en microservicios.

Un único proceso Node.js es suficiente.

---

# 16. Conexión MySQL

Utilizaremos un pool de conexiones.

Conceptualmente:

```text
Node.js
   │
   ▼
MySQL connection pool
   │
   ├── conexión
   ├── conexión
   └── conexión
```

El pool evita abrir y cerrar una conexión para cada petición.

El número de conexiones debe mantenerse bajo porque somos un único usuario.

No debemos configurar un pool enorme.

---

# 17. Endpoints definitivos

## Health

```http
GET /health
```

Comprueba que la aplicación está funcionando.

---

## Ubicación

```http
POST /api/location
GET /api/location
```

`POST` actualiza la última ubicación.

`GET` obtiene la última ubicación autorizada.

---

## Búsqueda

```http
GET /api/search
```

Busca una dirección o destino.

---

## Lugares cercanos

```http
GET /api/places/nearby
```

Ejemplo conceptual:

```text
/api/places/nearby?
lat=-28.123&
lon=153.123&
radius=500&
category=cafe
```

---

## Routing

```http
POST /api/routes
```

Entrada:

```json
{
  "origin": {
    "latitude": -28.123,
    "longitude": 153.123
  },
  "destination": {
    "latitude": -28.130,
    "longitude": 153.140
  },
  "mode": "driving"
}
```

El servidor solicita la ruta al proveedor seleccionado y devuelve solamente la información necesaria.

---

## Favoritos

```http
GET    /api/favorites
POST   /api/favorites
DELETE /api/favorites/:id
```

---

## IA

```http
POST /api/ai/query
```

Este endpoint es completamente opcional.

---

# 18. Navegación: qué NO debe hacer el servidor

El servidor no debe controlar cada segundo de la navegación.

No queremos:

```text
GPS
 ↓
Hostinger
 ↓
MySQL
 ↓
Hostinger
 ↓
Android
```

repetidamente durante toda la navegación.

Queremos:

```text
Android
 ├── GPS
 ├── mapa
 ├── seguimiento
 ├── instrucciones
 └── progreso

       │
       │ cuando sea necesario
       ▼

   Hostinger API
       │
       ▼
   Routing Provider
```

Esto reduce enormemente el tráfico y la carga.

---

# 19. Cálculo de ruta

La API tendrá una abstracción:

```text
calculateRoute(origin, destination, mode)
```

El proveedor podrá cambiarse posteriormente.

Por ejemplo:

```text
routingProvider
        │
        ├── OSRM
        ├── GraphHopper
        ├── Valhalla
        └── otro proveedor compatible
```

La aplicación no debe saber qué proveedor existe detrás.

---

# 20. Rerouting

El rerouting debe ser inteligente.

No debemos pedir una ruta nueva cada vez que cambia mínimamente el GPS.

La aplicación puede detectar:

```text
¿Estoy fuera de la ruta?
```

y solamente entonces solicitar:

```text
POST /api/routes
```

Esto reduce las llamadas al proveedor de routing.

---

# 21. Geocodificación y búsqueda

La búsqueda de destinos también debe pasar por un proveedor externo.

Ejemplo:

```text
Usuario escribe:
"123 Main Street"

        ↓

Android

        ↓

Hostinger API

        ↓

Geocoding Provider

        ↓

coordenadas

        ↓

Android
```

La API debe devolver solamente los resultados necesarios.

No debemos almacenar todas las búsquedas.

---

# 22. OpenStreetMap

OpenStreetMap será la base cartográfica.

Debemos distinguir:

```text
DATOS DE OPENSTREETMAP
```

de:

```text
SERVICIOS QUE UTILIZAN OSM
```

No debemos asumir que un servicio público puede utilizarse sin límites.

Debemos respetar siempre:

- políticas de uso;
- atribución;
- límites;
- identificación;
- condiciones del proveedor.

Para nuestro volumen personal, la arquitectura debe permitir cambiar de proveedor si fuera necesario.

---

# 23. Mapa en Android

El mapa debe renderizarse principalmente en Android.

Hostinger no servirá los tiles del mapa.

La arquitectura será:

```text
Android
   │
   ├── mapa
   ├── GPS
   └── interfaz
        │
        ▼
    Hostinger
        │
        ├── búsqueda
        ├── routing
        └── lugares
```

Esto mantiene el backend ligero.

---

# 24. Seguridad

Aunque sea una aplicación personal, la ubicación requiere protección.

Implementaremos:

```text
HTTPS
Bearer token
token almacenado como hash
validación de coordenadas
rate limiting
CORS limitado
variables de entorno
logs mínimos
```

Nunca:

```text
❌ token en GitHub
❌ contraseña en el código
❌ ubicación pública
❌ MySQL expuesto directamente a Internet
```

La base MySQL solo debe ser accesible por la aplicación.

---

# 25. Variables de entorno

Ejemplo:

```env
NODE_ENV=production

DB_HOST=...
DB_PORT=3306
DB_NAME=...
DB_USER=...
DB_PASSWORD=...

API_TOKEN=...

ROUTING_PROVIDER=...
ROUTING_API_KEY=...

PLACES_PROVIDER=...
PLACES_API_KEY=...

AI_ENABLED=false
AI_API_KEY=...
```

El `.env` nunca se sube al repositorio.

---

# 26. Optimización específica para un solo usuario

Esta es una ventaja importante.

No necesitamos optimizar para miles de usuarios.

Podemos diseñar para:

```text
1 usuario
1 teléfono
1 dispositivo activo
```

Eso permite:

- pool MySQL pequeño;
- pocas tablas;
- sin colas;
- sin Redis;
- sin WebSockets;
- sin balanceador;
- sin múltiples servidores;
- sin microservicios.

La optimización consiste en **eliminar trabajo**, no en montar infraestructura compleja.

---

# 27. Caché

No añadiremos Redis inicialmente.

Para un solo usuario, sería infraestructura innecesaria.

Podemos aplicar caché muy sencilla únicamente donde tenga sentido:

```text
búsqueda repetida
geocodificación repetida
ruta repetida
```

Si se implementa, debe tener TTL corto y tamaño limitado.

Primero mediremos el consumo antes de añadir una solución más compleja.

---

# 28. Logs

Los logs deben ser mínimos.

No registrar:

```text
❌ coordenadas completas
❌ tokens
❌ contraseñas
❌ respuestas completas de proveedores
```

Registrar solamente:

```text
request
endpoint
status
duración
error
```

y, cuando sea necesario, identificadores internos no sensibles.

---

# 29. Backups MySQL

Como inicialmente tendremos:

```text
última ubicación
favoritos
configuración
```

el backup será pequeño.

No necesitamos almacenar enormes cantidades de información.

Si posteriormente activamos historial, entonces revisaremos:

- retención;
- backups;
- exportación;
- eliminación automática.

---

# 30. IA opcional

La IA no participa en:

```text
GPS
mapa
routing
navegación
rerouting
```

Puede utilizarse para:

```text
"Buscame un café tranquilo."
"Quiero un restaurante barato."
"¿Qué tengo cerca?"
"Recomendame algo para comer."
```

La aplicación puede convertir la intención en una búsqueda estructurada.

La IA recibe solamente la información necesaria.

No debe recibir un flujo continuo de GPS.

---

# 31. Flujo principal de navegación

```text
1. Usuario abre la aplicación
        ↓
2. Android obtiene GPS
        ↓
3. Android muestra posición
        ↓
4. Usuario busca destino
        ↓
5. API de búsqueda devuelve coordenadas
        ↓
6. Android solicita ruta
        ↓
7. Hostinger consulta routing provider
        ↓
8. Hostinger devuelve ruta
        ↓
9. Android dibuja ruta
        ↓
10. Usuario inicia navegación
        ↓
11. Android sigue GPS localmente
        ↓
12. Si se desvía:
        ↓
13. Android solicita rerouting
        ↓
14. Nueva ruta
```

---

# 32. Flujo de lugares cercanos

```text
GPS Android
     ↓
coordenadas
     ↓
Hostinger API
     ↓
Places Provider
     ↓
resultados
     ↓
Android
```

Si el usuario selecciona un lugar:

```text
Lugar
 ↓
coordenadas
 ↓
Ruta
 ↓
Navegación
```

Así los lugares quedan integrados directamente con el navegador.

---

# 33. Flujo de IA

```text
Usuario
   ↓
"Buscame un café tranquilo"
   ↓
IA opcional
   ↓
criterios estructurados
   ↓
Places API
   ↓
resultados
   ↓
usuario elige
   ↓
routing
   ↓
navegación
```

La IA es una capa de interpretación, no el motor del navegador.

---

# 34. Estructura final simplificada

```text
                    ANDROID
                       │
             ┌─────────┴─────────┐
             │                   │
            GPS                MAPA
             │                   │
             └─────────┬─────────┘
                       │
                    HTTPS
                       │
                       ▼
              ┌─────────────────┐
              │    HOSTINGER    │
              │                 │
              │ Node.js         │
              │ Express         │
              │ API             │
              └───────┬─────────┘
                      │
          ┌───────────┼───────────┐
          │           │           │
          ▼           ▼           ▼
       MySQL       Routing     Places/
                   Provider    Geocoding
          │
          ▼
      favoritos
      settings
      dispositivo
      última ubicación

                 IA
                  │
                  │ opcional
                  ▼
              interpretación
```

---

# 35. Fases de desarrollo optimizadas

## Fase 1 — Hostinger + Node.js

Crear:

```text
GET /health
```

Objetivo:

```text
Android/cliente → Hostinger → OK
```

---

## Fase 2 — MySQL

Crear:

```text
users
devices
current_location
favorites
settings
```

Probar conexión desde Node.js.

---

## Fase 3 — autenticación

Implementar:

```text
Bearer Token
```

y protección de endpoints.

---

## Fase 4 — Android + GPS

Android obtiene:

```text
latitude
longitude
accuracy
timestamp
```

La ubicación se muestra localmente.

---

## Fase 5 — mapa

Mostrar:

```text
posición actual
```

---

## Fase 6 — búsqueda de destino

Implementar:

```text
GET /api/search
```

---

## Fase 7 — routing

Implementar:

```text
POST /api/routes
```

---

## Fase 8 — navegación

Implementar:

```text
GPS
seguimiento
distancia
tiempo
maniobra siguiente
voz
rerouting
```

La navegación debe funcionar sin IA.

---

## Fase 9 — lugares

Implementar:

```text
/api/places/nearby
```

---

## Fase 10 — favoritos

Implementar:

```text
Casa
Trabajo
lugares favoritos
```

---

## Fase 11 — IA opcional

Solo cuando todo lo anterior funcione.

---

# 36. MVP definitivo

El MVP debe poder:

```text
✓ Obtener GPS
✓ Mostrar mapa
✓ Mostrar posición
✓ Buscar destino
✓ Calcular ruta
✓ Dibujar ruta
✓ Iniciar navegación
✓ Mostrar distancia
✓ Mostrar tiempo estimado
✓ Mostrar próxima maniobra
✓ Detectar desvío
✓ Recalcular ruta
```

Además:

```text
✓ Lugares cercanos
✓ Seleccionar lugar
✓ Navegar hacia el lugar
```

La IA:

```text
○ opcional
```

---

# 37. Qué dejamos fuera del MVP

```text
❌ historial completo
❌ usuarios múltiples
❌ servidor de mapas propio
❌ servidor de routing propio
❌ Redis
❌ Docker
❌ microservicios
❌ Kubernetes
❌ PostGIS
❌ IA obligatoria
❌ tráfico propio
❌ analítica avanzada
❌ sincronización entre múltiples teléfonos
```

Cada elemento podrá incorporarse si aparece una necesidad real.

---

# 38. Evolución futura

Si el proyecto crece:

```text
HOSTINGER
   ↓
VPS
   ↓
routing propio
   ↓
OSM local
   ↓
PostGIS
   ↓
caché
   ↓
servicios adicionales
```

Pero esto será una migración posterior.

No debemos diseñar el MVP como si ya necesitáramos esa infraestructura.

---

# 39. Decisiones técnicas definitivas

```text
Sistema de desarrollo     Zorin OS / Linux
Backend                   Node.js
Framework                 Express
Hosting                   Hostinger
Base de datos             MySQL
Aplicación                Android
GPS                       GPS del teléfono
Mapa                      OpenStreetMap + proveedor compatible
Routing                   proveedor externo intercambiable
Places                    proveedor externo intercambiable
Geocoding                 proveedor externo intercambiable
HTTPS                     Hostinger
IA                        opcional
VPS                       no necesario inicialmente
```

---

# 40. Regla principal del proyecto

La regla de arquitectura será:

> **El teléfono hace el trabajo de navegación. Hostinger coordina los servicios. MySQL guarda solamente lo que realmente necesitamos.**

Y una segunda regla:

> **No añadiremos infraestructura hasta que exista una necesidad técnica demostrable.**

El objetivo no es construir una plataforma de mapas.

El objetivo es construir un **navegador GPS personal, ligero, privado y mantenible**, utilizando Hostinger como backend y MySQL como almacenamiento persistente.

---

# 41. Arquitectura económica final

```text
                  ┌───────────────┐
                  │    ANDROID    │
                  │               │
                  │ GPS + MAPA    │
                  │ NAVEGACIÓN    │
                  └───────┬───────┘
                          │
                         HTTPS
                          │
                          ▼
                 ┌─────────────────┐
                 │    HOSTINGER    │
                 │                 │
                 │ Node + Express  │
                 └───────┬─────────┘
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
            MySQL     Routing     Places
                         │
                         ▼
                       OSM

                         +

                    IA OPCIONAL
```

Esta es la estructura recomendada para la primera versión de producción.

No necesitamos comprar un VPS para empezar.

## Capa de puntos personalizados

Además de los datos cartográficos y de los servicios externos, la aplicación puede mantener una capa propia de puntos definidos por el usuario. Esta capa está pensada para lugares que queremos recordar o para avisos útiles durante la navegación, por ejemplo:

- lugares favoritos;
- casa, trabajo u otros destinos frecuentes;
- cámaras de velocidad;
- cámaras de semáforo;
- zonas de peligro;
- colegios o zonas sensibles;
- gasolineras o parkings que queramos guardar;
- descansos/paradas;
- cualquier punto personalizado.

### Principio de diseño

Los puntos personales se almacenan en MySQL y se diferencian claramente de los datos de OSM o de otros proveedores.

```text
Datos externos
    ├── OSM / proveedor cartográfico
    ├── búsqueda y geocodificación
    └── routing

Datos personales
    └── map_points
          ├── favoritos
          ├── cámaras
          ├── peligros
          └── puntos personalizados
```

Esto evita modificar o duplicar datos externos y permite que los puntos personales sigan siendo propiedad/configuración de la aplicación.

### Tabla `map_points`

Para mantener el sistema pequeño y eficiente se utiliza una sola tabla genérica, en lugar de crear una tabla independiente para cada tipo de punto.

```text
map_points
--------------------------------
id
user_id
type
name
latitude
longitude
description
enabled
alert_enabled
direction
source
verified_at
created_at
updated_at
```

Valores posibles de `type`:

```text
favorite
camera
speed_camera
traffic_light_camera
danger
school_zone
fuel
parking
rest_area
custom
```

Campos importantes:

- `latitude` / `longitude`: posición del punto.
- `type`: determina cómo debe tratarse y mostrarse.
- `enabled`: permite desactivar temporalmente un punto sin borrarlo.
- `alert_enabled`: permite decidir si genera aviso durante la navegación.
- `direction`: opcional; útil para cámaras o avisos que solamente aplican a un sentido.
- `source`: indica si el punto procede del usuario, de una importación o de una fuente externa.
- `verified_at`: permite distinguir puntos revisados de puntos pendientes de verificar.
- `description`: notas adicionales.
- `created_at` / `updated_at`: mantenimiento del registro.

Para un único usuario y un volumen pequeño no se necesita una estructura más compleja.

### API de puntos

Se añaden únicamente los endpoints necesarios:

```text
GET    /api/map-points?lat=&lon=&radius=&type=
POST   /api/map-points
PATCH  /api/map-points/:id
DELETE /api/map-points/:id
```

Ejemplos de uso:

```text
"Guardar este lugar"
    → POST /api/map-points

"Marcar cámara aquí"
    → POST /api/map-points
       type = speed_camera

"Desactivar esta cámara"
    → PATCH /api/map-points/:id

"Eliminar este punto"
    → DELETE /api/map-points/:id
```

### Optimización para consumir lo mínimo

Los puntos no se consultan continuamente contra Hostinger.

Durante la navegación:

1. Android recibe/calcula la ruta.
2. Android solicita al servidor solamente los puntos relevantes para esa zona.
3. El servidor devuelve un conjunto pequeño de puntos.
4. Android los mantiene temporalmente en memoria/caché local.
5. La aplicación comprueba localmente si se aproxima a un punto.
6. Solamente se vuelve a consultar cuando la ruta cambia de forma significativa o se sale de la zona previamente cargada.

La aplicación **no debe enviar un GPS cada segundo al servidor para comprobar cámaras**.

Para una ruta activa es preferible cargar un pequeño corredor alrededor de la ruta, en lugar de descargar todos los puntos de una ciudad o consultar un radio diminuto en cada actualización GPS.

```text
                 punto personalizado
                         ●
                        / \
                       /   \
======================/===== \======================
              corredor de la ruta
                    RUTA ACTIVA
```

El ancho del corredor debe mantenerse pequeño y configurable. Esto reduce:

- consultas a MySQL;
- tráfico HTTPS;
- uso de batería;
- procesamiento en Android;
- carga del hosting.

### Consultas geográficas

En la primera versión se prioriza la simplicidad:

- índices sobre `user_id`, `type`, `enabled`;
- consulta por zona aproximada;
- filtrado final por distancia en el servidor o en Android.

Si el número de puntos creciera mucho, se puede evolucionar a tipos espaciales e índices espaciales de MySQL sin cambiar la API pública.

Para el caso actual de un único usuario, **no se justifica introducir complejidad espacial avanzada desde el principio**.

### Avisos durante la navegación

Los avisos de puntos personalizados son responsabilidad de Android una vez que los puntos relevantes están en caché.

Ejemplo:

```text
GPS actual
   ↓
¿Hay un punto de alerta cercano?
   ↓ sí
¿Está habilitado?
   ↓ sí
¿Aplica al sentido de circulación?
   ↓ sí / si no hay dirección definida
Aviso visual + voz
```

La distancia de aviso debe ser configurable según el tipo de punto. No todos los puntos necesitan aviso por voz.

Ejemplo:

```text
speed_camera
    → aviso anticipado

danger
    → aviso anticipado

favorite
    → normalmente sin aviso automático
```

### Precisión y fiabilidad de las cámaras

Las cámaras guardadas por el usuario deben considerarse **puntos personales**, no una base oficial universal.

Por ello:

- `source` permite conocer el origen;
- `verified_at` permite registrar una verificación;
- la aplicación no debe afirmar que una cámara es correcta simplemente porque existe en MySQL;
- si en el futuro se importa una fuente oficial o abierta, se puede conservar su procedencia.

Esto permite combinar datos personales con fuentes externas sin mezclarlos ni perder su trazabilidad.

### Privacidad

Los puntos personalizados son datos privados del usuario.

No se envían a servicios de IA ni a proveedores externos salvo que una función concreta lo necesite y el usuario lo permita.

La aplicación tampoco necesita enviar continuamente al servidor qué cámara está viendo o qué punto acaba de detectar. Una vez descargados los puntos relevantes, esa comprobación puede hacerse localmente en Android.

### Integración con el MVP

La capa de puntos personalizados queda integrada en el navegador desde el principio, pero sin hacerla crítica para la navegación.

MVP:

1. Crear punto desde la posición actual.
2. Elegir tipo.
3. Guardar nombre/notas.
4. Mostrar puntos en el mapa.
5. Cargar puntos relevantes de la ruta.
6. Avisar de puntos con `alert_enabled`.
7. Editar/desactivar/eliminar puntos.
8. Navegar hacia un punto guardado.

La navegación sigue funcionando aunque MySQL o el endpoint de puntos estén temporalmente fuera de servicio.

### Regla de eficiencia

```text
GPS → Android
navegación → Android
detección de proximidad → Android
voz → Android

persistencia de puntos → MySQL
sincronización de puntos → Hostinger API
búsqueda externa → Hostinger/proveedor
routing → proveedor de routing
IA → opcional
```

Esta separación mantiene Hostinger como coordinador ligero y deja en el teléfono las tareas que no necesitan conexión continua.
