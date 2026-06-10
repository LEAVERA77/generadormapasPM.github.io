package com.topoarg.app.crs

import org.locationtech.proj4j.CRSFactory
import org.locationtech.proj4j.CoordinateTransform
import org.locationtech.proj4j.CoordinateTransformFactory
import org.locationtech.proj4j.ProjCoordinate

/** Resultado de una conversión. Para CRS proyectados: north/east en metros. */
data class ConvertedCoordinate(
    val crs: CrsDef,
    /** X en convención argentina = Norte (m), o latitud en grados si es geográfico. */
    val north: Double,
    /** Y en convención argentina = Este (m), o longitud en grados si es geográfico. */
    val east: Double
)

/**
 * Conversor de coordenadas basado en proj4j. Parte siempre de WGS84 geográficas
 * (lo que entrega el GPS del teléfono) hacia cualquier CRS del catálogo,
 * aplicando la transformación de datum de 7 parámetros definida en cada CRS.
 */
object CoordinateConverter {

    private val crsFactory = CRSFactory()
    private val transformFactory = CoordinateTransformFactory()

    private val wgs84 = crsFactory.createFromParameters(
        "WGS84", "+proj=longlat +datum=WGS84 +no_defs"
    )

    private val transformCache = HashMap<Int, CoordinateTransform>()

    @Synchronized
    private fun transformFor(def: CrsDef): CoordinateTransform =
        transformCache.getOrPut(def.epsg) {
            val target = crsFactory.createFromParameters(def.id, def.proj4)
            transformFactory.createTransform(wgs84, target)
        }

    /**
     * Convierte lat/lon WGS84 (grados) al CRS destino.
     * proj4j devuelve x = Este / longitud, y = Norte / latitud;
     * aquí lo mapeamos a la convención argentina (X = Norte, Y = Este).
     */
    fun fromWgs84(lat: Double, lon: Double, def: CrsDef): ConvertedCoordinate {
        val src = ProjCoordinate(lon, lat)
        val dst = ProjCoordinate()
        transformFor(def).transform(src, dst)
        return ConvertedCoordinate(def, north = dst.y, east = dst.x)
    }

    /** Convierte un punto a todos los CRS del catálogo (para la vista de detalle). */
    fun toAllSystems(lat: Double, lon: Double): List<ConvertedCoordinate> =
        CrsCatalog.all.map { fromWgs84(lat, lon, it) }
}
