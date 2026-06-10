package com.topoarg.app.location

import kotlin.math.sqrt

/**
 * Parser NMEA 0183 mínimo para receptores GNSS externos.
 * Soporta GGA (posición, calidad de fix, satélites, alturas) de cualquier
 * talker (GP/GN/GL/GA/GB...) y GST (desvíos estándar de la solución).
 */
object NmeaParser {

    data class GgaData(
        val lat: Double,
        val lon: Double,
        /** Campo 6 de GGA: 1 autónomo, 2 DGNSS, 4 RTK fijo, 5 RTK flotante… */
        val quality: Int,
        val satellites: Int,
        val hdop: Double?,
        /** Altura elipsoidal h = altura MSL + separación del geoide (campos 9 y 11). */
        val ellipsoidalHeight: Double,
        /** Altura ortométrica reportada por el receptor (campo 9). */
        val orthometricHeight: Double
    )

    /** Valida el checksum XOR si la sentencia lo trae. */
    fun checksumOk(line: String): Boolean {
        if (!line.startsWith("$")) return false
        val star = line.lastIndexOf('*')
        if (star < 0) return true // sin checksum: se acepta
        if (star + 3 > line.length) return false
        var sum = 0
        for (i in 1 until star) sum = sum xor line[i].code
        return line.substring(star + 1, star + 3).toIntOrNull(16) == sum
    }

    private fun fields(line: String): List<String> =
        line.substringAfter('$').substringBefore('*').split(',')

    fun isGga(line: String): Boolean =
        line.startsWith("$") && fields(line).firstOrNull()?.endsWith("GGA") == true

    fun isGst(line: String): Boolean =
        line.startsWith("$") && fields(line).firstOrNull()?.endsWith("GST") == true

    /**
     * Parsea una sentencia GGA. Devuelve null si es inválida o sin fix.
     * Formato: $xxGGA,hora,lat,N/S,lon,E/W,calidad,sats,hdop,altMSL,M,sepGeoide,M,...
     */
    fun parseGga(line: String): GgaData? {
        val f = fields(line)
        if (f.size < 10 || !f[0].endsWith("GGA")) return null
        val quality = f[6].toIntOrNull() ?: return null
        if (quality == 0) return null
        val lat = parseCoordinate(f[2], f[3]) ?: return null
        val lon = parseCoordinate(f[4], f[5]) ?: return null
        val sats = f[7].toIntOrNull() ?: 0
        val hdop = f[8].toDoubleOrNull()
        val msl = f[9].toDoubleOrNull() ?: 0.0
        val geoidSep = f.getOrNull(11)?.toDoubleOrNull() ?: 0.0
        return GgaData(
            lat = lat, lon = lon, quality = quality, satellites = sats,
            hdop = hdop, ellipsoidalHeight = msl + geoidSep, orthometricHeight = msl
        )
    }

    /**
     * Parsea GST y devuelve el desvío estándar horizontal (m):
     * sqrt(stdLat² + stdLon²), campos 6 y 7.
     */
    fun parseGstHorizontal(line: String): Float? {
        val f = fields(line)
        if (f.size < 8 || !f[0].endsWith("GST")) return null
        val latStd = f[6].toDoubleOrNull() ?: return null
        val lonStd = f[7].toDoubleOrNull() ?: return null
        return sqrt(latStd * latStd + lonStd * lonStd).toFloat()
    }

    /** Convierte ddmm.mmmm + hemisferio a grados decimales con signo. */
    fun parseCoordinate(value: String, hemisphere: String): Double? {
        val raw = value.toDoubleOrNull() ?: return null
        if (value.isBlank()) return null
        val deg = (raw / 100.0).toInt()
        val minutes = raw - deg * 100.0
        var result = deg + minutes / 60.0
        if (hemisphere == "S" || hemisphere == "W") result = -result
        return result
    }
}
