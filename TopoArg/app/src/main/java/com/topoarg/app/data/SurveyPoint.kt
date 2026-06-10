package com.topoarg.app.data

/**
 * Punto medido. Las coordenadas se almacenan SIEMPRE en WGS84 geográficas
 * (lo que entrega el receptor GNSS); la conversión a Gauss-Krüger / UTM se
 * hace al mostrar o exportar, lo que permite cambiar de sistema sin pérdida.
 */
data class SurveyPoint(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val lat: Double,
    val lon: Double,
    /** Altura elipsoidal WGS84 en metros (la que entrega el GPS). */
    val altitude: Double,
    /** Precisión horizontal estimada (m, 1 sigma). */
    val accuracy: Float,
    /** Precisión vertical estimada (m, 1 sigma) o 0 si no está disponible. */
    val verticalAccuracy: Float,
    /** Cantidad de épocas promediadas (1 = punto instantáneo). */
    val samples: Int,
    /** Desvío estándar horizontal del promediado (m), 0 si fue instantáneo. */
    val stdHorizontal: Double,
    /** Época de la medición (epoch millis). */
    val timestamp: Long
)
