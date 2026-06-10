package com.topoarg.app.crs

import kotlin.math.roundToInt

/**
 * Definición de un sistema de referencia de coordenadas (CRS).
 *
 * @param epsg      Código EPSG oficial.
 * @param name      Nombre legible.
 * @param group     Grupo/datum al que pertenece.
 * @param proj4     Cadena PROJ.4 con parámetros de proyección y transformación de datum.
 * @param projected true si es un sistema proyectado (Gauss-Krüger / UTM), false si es geográfico.
 * @param faja      Número de faja Gauss-Krüger (1..7) o null si no aplica.
 * @param datumKey  Clave del datum, para poder buscar la faja "hermana" en el mismo datum.
 */
data class CrsDef(
    val epsg: Int,
    val name: String,
    val group: String,
    val proj4: String,
    val projected: Boolean,
    val faja: Int? = null,
    val datumKey: String = ""
) {
    val id: String get() = "EPSG:$epsg"
    override fun toString(): String = "$name (EPSG:$epsg)"
}

/**
 * Catálogo de todos los marcos de referencia usados históricamente en Argentina,
 * desde Campo Inchauspe (1969) hasta POSGAR 2007 (el marco oficial vigente),
 * con las 7 fajas Gauss-Krüger de cada uno, más UTM y los sistemas geográficos.
 *
 * Convención argentina Gauss-Krüger:
 *   - Proyección Transversa de Mercator, k = 1, origen de latitudes en el Polo Sur (lat_0 = -90).
 *   - Meridiano central de la faja N: -75° + 3°·N  (faja 1 = -72°, ..., faja 7 = -54°).
 *   - Falso Este: N·1.000.000 + 500.000 m. Falso Norte: 0 m.
 *   - X = Norte, Y = Este (¡convención inversa a la matemática habitual!).
 */
object CrsCatalog {

    const val DATUM_INCHAUSPE = "inchauspe"
    const val DATUM_POSGAR94 = "posgar94"
    const val DATUM_POSGAR98 = "posgar98"
    const val DATUM_POSGAR2007 = "posgar2007"
    const val DATUM_WGS84 = "wgs84"

    private fun lonCentral(faja: Int) = -75 + 3 * faja

    private fun gkProj4(faja: Int, ellps: String, towgs84: String): String {
        val falsoEste = faja * 1_000_000 + 500_000
        return "+proj=tmerc +lat_0=-90 +lon_0=${lonCentral(faja)} +k=1 " +
            "+x_0=$falsoEste +y_0=0 +ellps=$ellps +towgs84=$towgs84 +units=m +no_defs"
    }

    // Parámetros oficiales de transformación a WGS84 (EPSG):
    private const val TOWGS84_INCHAUSPE = "-148,136,90,0,0,0,0"
    private const val TOWGS84_IDENTITY = "0,0,0,0,0,0,0"

    val geographic: List<CrsDef> = listOf(
        CrsDef(
            4326, "WGS 84 (geográficas)", "Geográficas",
            "+proj=longlat +datum=WGS84 +no_defs", false, datumKey = DATUM_WGS84
        ),
        CrsDef(
            5340, "POSGAR 2007 (geográficas)", "Geográficas",
            "+proj=longlat +ellps=GRS80 +towgs84=$TOWGS84_IDENTITY +no_defs",
            false, datumKey = DATUM_POSGAR2007
        ),
        CrsDef(
            4190, "POSGAR 98 (geográficas)", "Geográficas",
            "+proj=longlat +ellps=GRS80 +towgs84=$TOWGS84_IDENTITY +no_defs",
            false, datumKey = DATUM_POSGAR98
        ),
        CrsDef(
            4694, "POSGAR 94 (geográficas)", "Geográficas",
            "+proj=longlat +ellps=WGS84 +towgs84=$TOWGS84_IDENTITY +no_defs",
            false, datumKey = DATUM_POSGAR94
        ),
        CrsDef(
            4221, "Campo Inchauspe (geográficas)", "Geográficas",
            "+proj=longlat +ellps=intl +towgs84=$TOWGS84_INCHAUSPE +no_defs",
            false, datumKey = DATUM_INCHAUSPE
        )
    )

    val posgar2007: List<CrsDef> = (1..7).map { f ->
        CrsDef(
            5342 + f, "POSGAR 2007 / Faja $f", "POSGAR 2007 – Gauss-Krüger",
            gkProj4(f, "GRS80", TOWGS84_IDENTITY), true, faja = f, datumKey = DATUM_POSGAR2007
        )
    }

    val posgar98: List<CrsDef> = (1..7).map { f ->
        CrsDef(
            22170 + f, "POSGAR 98 / Faja $f", "POSGAR 98 – Gauss-Krüger",
            gkProj4(f, "GRS80", TOWGS84_IDENTITY), true, faja = f, datumKey = DATUM_POSGAR98
        )
    }

    val posgar94: List<CrsDef> = (1..7).map { f ->
        CrsDef(
            22180 + f, "POSGAR 94 / Faja $f", "POSGAR 94 – Gauss-Krüger",
            gkProj4(f, "WGS84", TOWGS84_IDENTITY), true, faja = f, datumKey = DATUM_POSGAR94
        )
    }

    val campoInchauspe: List<CrsDef> = (1..7).map { f ->
        CrsDef(
            22190 + f, "Campo Inchauspe / Faja $f", "Campo Inchauspe – Gauss-Krüger",
            gkProj4(f, "intl", TOWGS84_INCHAUSPE), true, faja = f, datumKey = DATUM_INCHAUSPE
        )
    }

    val utm: List<CrsDef> = (18..21).map { z ->
        CrsDef(
            32700 + z, "UTM zona ${z}S (WGS84)", "UTM (WGS84)",
            "+proj=utm +zone=$z +south +datum=WGS84 +units=m +no_defs",
            true, datumKey = DATUM_WGS84
        )
    }

    /** Lista completa, en orden de presentación. */
    val all: List<CrsDef> =
        posgar2007 + posgar98 + posgar94 + campoInchauspe + utm + geographic

    val default: CrsDef = posgar2007[4] // Faja 5 (centro del país)

    fun byEpsg(epsg: Int): CrsDef = all.firstOrNull { it.epsg == epsg } ?: default

    /**
     * Calcula la faja Gauss-Krüger que corresponde a una longitud dada.
     * Faja N cubre el rango [-75 + 3N - 1.5, -75 + 3N + 1.5].
     */
    fun fajaForLongitude(lon: Double): Int =
        ((lon + 75.0) / 3.0).roundToInt().coerceIn(1, 7)

    /**
     * Devuelve la definición "hermana" de [def] en la faja indicada
     * (mismo datum). Si [def] no es Gauss-Krüger, devuelve [def] sin cambios.
     */
    fun withFaja(def: CrsDef, faja: Int): CrsDef {
        if (def.faja == null) return def
        return all.firstOrNull { it.datumKey == def.datumKey && it.faja == faja } ?: def
    }

    /**
     * CRS efectivo a usar: si [autoFaja] está activo y el CRS es Gauss-Krüger,
     * selecciona automáticamente la faja según la longitud actual.
     */
    fun effective(def: CrsDef, lon: Double?, autoFaja: Boolean): CrsDef {
        if (!autoFaja || def.faja == null || lon == null) return def
        return withFaja(def, fajaForLongitude(lon))
    }
}
