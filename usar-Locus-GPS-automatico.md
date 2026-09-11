# Usar Locus GPS automáticamente con DoorDash, Chrome y Tasker

Esta guía configura un modo personal para que, mientras se está haciendo delivery, Tasker vigile Chrome y detecte los enlaces de navegación de Waze que abre DoorDash.

## Problema que queremos resolver

DoorDash permite seleccionar servicios de navegación como Google Maps o Waze. En el teléfono utilizado para este proyecto Waze no está instalado, por lo que DoorDash abre Chrome con una dirección web de Waze en lugar de abrir Locus GPS.

El enlace tiene este formato:

```text
https://www.waze.com/ul?ll=-28.061203,153.43167&navigate=yes
```

Locus GPS no puede hacerse pasar por Waze ni controlar el dominio `waze.com`. Android solo permite que una aplicación abra automáticamente los enlaces HTTPS de dominios que la aplicación posee y verifica. Por eso no se debe intentar interceptar o falsificar Waze.

## Solución que buscamos

Crear una automatización personal y controlable que funcione como puente:

```text
DoorDash
    ↓
Chrome abre el enlace de Waze
    ↓
Tasker + AutoInput detectan la URL
    ↓
Se extraen latitud y longitud
    ↓
Popup de confirmación
    ↓
Locus GPS recibe el destino
    ↓
Locus calcula y muestra la ruta
```

La automatización debe estar activa solamente durante el delivery. Fuera de ese modo, Tasker no debe vigilar Chrome ni procesar ubicaciones.

## Objetivo

DoorDash abre una URL como:

```text
https://www.waze.com/ul?ll=-28.061203,153.43167&navigate=yes
```

Tasker y AutoInput deben:

1. Activarse solamente durante el delivery.
2. Detectar la URL de Waze en Chrome.
3. Extraer latitud y longitud.
4. Mostrar una confirmación.
5. Abrir Locus GPS con el destino.

El enlace que recibe Locus GPS es:

```text
locusgps://navigate?lat=-28.061203&lon=153.43167
```

Locus GPS también admite enlaces compartidos de Waze y enlaces `geo:`.

## Ruta nativa recomendada (sin Tasker ni AutoInput)

Locus GPS ahora puede interpretar localmente estos formatos cuando Android le entrega el intent:

- `locusgps://navigate?lat=...&lon=...`
- `geo:LAT,LON`
- `https://locusgps.pro/navigate?lat=...&lon=...`
- `https://www.waze.com/ul?ll=LAT,LON&navigate=yes`

Al recibir un destino externo, la app muestra una confirmación **Destino detectado / Cancelar / Navegar** y solo calcula la ruta si se pulsa **Navegar**. Las coordenadas nunca se envían a un servicio adicional para ser interpretadas.

Importante: que Locus pueda interpretar la URL no significa que Android vaya a entregarle automáticamente cualquier enlace de `waze.com`. Chrome, DoorDash y Android deciden qué aplicación recibe un `ACTION_VIEW`; Locus no puede reclamar ni verificar el dominio de Waze. Primero debe probarse en el teléfono real. Si Android continúa abriendo Chrome, la ruta nativa no puede forzarse desde Locus.

### Prueba manual de la ruta nativa

Instalar un APK actualizado y abrir desde otra aplicación:

```text
https://www.waze.com/ul?ll=-28.061203,153.43167&navigate=yes
```

También se puede compartir esa URL hacia Locus GPS. Si Android ofrece Locus como destino del texto compartido, la app extraerá `ll` y mostrará la confirmación.

## Aplicaciones opcionales

- [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm)
- [AutoInput](https://play.google.com/store/apps/details?id=com.joaomgcd.autoinput)

## Permisos iniciales

### AutoInput

En Android/Samsung:

```text
Ajustes
→ Accesibilidad
→ Aplicaciones instaladas
→ AutoInput
→ Activar
```

### Batería

Para Tasker y AutoInput:

```text
Ajustes
→ Aplicaciones
→ Tasker
→ Batería
→ Sin restricciones
```

Repetir con AutoInput. También conviene permitir las notificaciones de Tasker.

## Modo Delivery

Se utilizan dos perfiles de Tasker y una variable global:

```text
%DELIVERY_MODE
```

### Activar al abrir DoorDash

1. Abrir Tasker.
2. Ir a **Profiles**.
3. Pulsar **+**.
4. Elegir **Application**.
5. Seleccionar DoorDash.
6. Crear una tarea llamada `DELIVERY_ON`.
7. Añadir la acción **Variables → Variable Set**.

Valores:

```text
Name: %DELIVERY_MODE
To: 1
```

### Desactivar al salir de DoorDash

Mantener pulsado el perfil de DoorDash y seleccionar **Add Exit Task**.

Crear `DELIVERY_OFF` con:

```text
Name: %DELIVERY_MODE
To: 0
```

Así el sistema queda apagado fuera del trabajo.

## Vigilancia de Chrome con AutoInput

Crear un perfil adicional:

```text
Profiles
→ +
→ Event
→ Plugin
→ AutoInput
→ UI Update
```

Configurar Chrome como aplicación vigilada y limitar la condición a:

```text
%DELIVERY_MODE ~ 1
```

El texto buscado debe contener:

```text
waze.com/ul?ll=
```

AutoInput puede consultar los textos visibles de la interfaz mediante UI Query/UI Update. Los nombres exactos de las variables pueden variar según la versión; normalmente se utiliza `%aitext()`.

## Extraer coordenadas

Buscar este patrón:

```regex
ll=(-?[0-9]+(?:\.[0-9]+)?),(-?[0-9]+(?:\.[0-9]+)?)
```

El resultado esperado es:

```text
Latitud: -28.061203
Longitud: 153.43167
```

Si la versión de Tasker no separa automáticamente los grupos, se puede usar una acción **JavaScriptlet**:

```javascript
const texto = global('PANTALLA') || '';
const encontrado = texto.match(/ll=(-?[0-9]+(?:\.[0-9]+)?),(-?[0-9]+(?:\.[0-9]+)?)/);

if (encontrado) {
  setGlobal('WAZE_LAT', encontrado[1]);
  setGlobal('WAZE_LON', encontrado[2]);
  setGlobal('WAZE_FOUND', '1');
} else {
  setGlobal('WAZE_FOUND', '0');
}
```

Antes de ejecutar el JavaScriptlet, guardar el texto detectado en `%PANTALLA`.

## Confirmación antes de navegar

No abrir la ruta automáticamente sin confirmación. Usar una acción de menú/diálogo con:

```text
¿Navegar con Locus GPS?

Ubicación detectada:
%WAZE_LAT, %WAZE_LON

[Cancelar] [Aceptar]
```

Si se pulsa **Aceptar**, utilizar la acción de Tasker **Browse URL** con:

```text
locusgps://navigate?lat=%WAZE_LAT&lon=%WAZE_LON
```

Si se pulsa **Cancelar**, no hacer nada.

## Evitar duplicados

Guardar la última coordenada procesada en:

```text
%LAST_WAZE_LOCATION
```

Antes de mostrar el diálogo, comparar:

```text
%WAZE_LAT,%WAZE_LON
```

con `%LAST_WAZE_LOCATION`. Si son iguales, no repetir la acción. Después de aceptar, actualizar `%LAST_WAZE_LOCATION`.

También conviene añadir un tiempo mínimo de 20 a 30 segundos entre detecciones.

## Alternativa más estable: compartir

Si AutoInput deja de detectar la barra de URL después de una actualización de Chrome:

1. Abrir el enlace de Waze en Chrome.
2. Pulsar **Compartir**.
3. Elegir Locus GPS o Tasker.
4. Extraer las coordenadas del texto compartido.
5. Abrir Locus GPS con `locusgps://navigate?...`.

Esta alternativa requiere un toque adicional, pero no necesita vigilar Chrome continuamente.

## Prueba controlada

Usar este enlace:

```text
https://www.waze.com/ul?ll=-28.061203,153.43167&navigate=yes
```

Resultado esperado:

1. DoorDash/Chrome muestra el enlace.
2. AutoInput lo detecta solamente con `%DELIVERY_MODE = 1`.
3. Tasker muestra el diálogo.
4. Al aceptar, Locus GPS calcula una ruta hacia esas coordenadas.

## Seguridad y privacidad

- El modo debe permanecer apagado fuera del delivery.
- No guardar historial de URLs ni ubicaciones salvo que sea necesario.
- No enviar las URLs de Waze a servidores externos.
- No usar una VPN ni interceptar tráfico HTTPS.
- No intentar hacerse pasar por Waze ni registrar `waze.com` como dominio propio.
- Mantener siempre la confirmación antes de iniciar una ruta.

## Limitaciones

AutoInput depende de la interfaz visible de Chrome. Chrome puede cambiar los identificadores o esconder la URL, y Android puede limitar el inicio de aplicaciones en segundo plano. Si deja de funcionar, utilizar la alternativa de compartir.

La automatización es para uso personal y no sustituye las señales, límites de velocidad ni las indicaciones oficiales de tránsito.
