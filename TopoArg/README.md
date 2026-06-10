# TopoArg — GPS para agrimensura (Argentina)

App Android nativa (Kotlin) para toma de puntos GPS estilo *Mobile Topographer*,
pensada para agrimensores que trabajan con los marcos de referencia argentinos.

## Características

- **Todos los sistemas de coordenadas de Argentina**, del más antiguo al vigente:
  - **Campo Inchauspe** (1969) — Gauss-Krüger fajas 1 a 7 (EPSG:22191–22197) y geográficas (EPSG:4221).
  - **POSGAR 94** — fajas 1 a 7 (EPSG:22181–22187) y geográficas (EPSG:4694).
  - **POSGAR 98** — fajas 1 a 7 (EPSG:22171–22177) y geográficas (EPSG:4190).
  - **POSGAR 2007** (marco oficial vigente, Ley 26.696/IGN) — fajas 1 a 7 (EPSG:5343–5349) y geográficas (EPSG:5340).
  - **UTM zonas 18S a 21S** sobre WGS84 (EPSG:32718–32721) y WGS84 geográficas (EPSG:4326).
- **Faja Gauss-Krüger automática**: elige sola la faja (1–7) según la longitud del receptor, manteniendo el datum elegido.
- **Promediado de posiciones** (épocas a 1 Hz) con:
  - cantidad de épocas configurable,
  - filtro por precisión horizontal máxima,
  - desvío estándar horizontal en tiempo real,
  - conteo de épocas rechazadas.
- **Toma instantánea de puntos** con nombre y descripción/código.
- **Detalle de cada punto convertido a *todos* los sistemas** del catálogo a la vez.
- **Estado GNSS**: satélites usados/visibles, precisión horizontal y vertical.
- **Exportación y compartido** por WhatsApp/correo/Drive en:
  - **CSV** (con Norte/Este Gauss-Krüger + lat/lon WGS84),
  - **DXF** (AutoCAD, en coordenadas proyectadas),
  - **KML** (Google Earth), **GPX** y **GeoJSON** (QGIS).
- Persistencia local en SQLite; las coordenadas se guardan siempre en WGS84 y se
  convierten al vuelo, así se puede cambiar de sistema sin pérdida.

## Motor de coordenadas

Las conversiones usan [proj4j](https://github.com/locationtech/proj4j) con las
definiciones PROJ.4 oficiales de cada EPSG, incluida la transformación de datum
de Campo Inchauspe a WGS84 (ΔX=-148, ΔY=136, ΔZ=90, parámetros EPSG). La
proyección Gauss-Krüger argentina se modela como Transversa de Mercator con
`lat_0=-90`, `k=1`, falso Este `faja·1.000.000 + 500.000 m` y meridianos
centrales -72° a -54°.

**Convención de ejes**: en la pantalla y el CSV, `X = Norte` e `Y = Este`
(convención argentina). En el DXF, Este→X CAD y Norte→Y CAD (convención de dibujo).

## Alturas

Las alturas mostradas/exportadas son **elipsoidales (WGS84)**, tal como las
entrega el chip GNSS del teléfono. Para cotas sobre el nivel medio del mar debe
aplicarse el modelo de geoide del IGN (GEOIDE-Ar 16).

## Requisitos y compilación

- Android Studio (Hedgehog o más nuevo) con JDK 17+.
- Android 8.0 (API 26) o superior en el dispositivo.

```bash
# Desde Android Studio: File ▸ Open ▸ carpeta TopoArg/  y Run ▸ app
# O por línea de comandos:
./gradlew assembleDebug
# APK resultante: app/build/outputs/apk/debug/app-debug.apk
```

## Estructura

```
app/src/main/java/com/topoarg/app/
├── crs/        Catálogo EPSG argentino + conversor proj4j
├── location/   Motor GNSS (FusedLocation + GnssStatus)
├── data/       Modelo, SQLite y repositorio de puntos
├── export/     Generadores CSV / KML / GPX / GeoJSON / DXF
├── settings/   Preferencias (CRS, faja automática, promediado)
├── util/       Formato DMS / metros / fechas
└── ui/         Medición, Puntos, Exportar, Ajustes
```

## Precisión

La precisión está limitada por el hardware GNSS del teléfono (típicamente
3–10 m autónomo; 1–3 m con promediado en cielo abierto). Para trabajos que
exijan precisión geodésica debe usarse un receptor RTK externo.
