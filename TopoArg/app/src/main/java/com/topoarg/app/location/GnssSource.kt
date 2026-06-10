package com.topoarg.app.location

import android.location.Location
import androidx.lifecycle.LiveData

/** Calidad del fix según el campo 6 de la sentencia NMEA GGA. */
enum class FixQuality(val gga: Int, val label: String) {
    UNKNOWN(-1, "Sin datos"),
    INVALID(0, "Sin posición"),
    AUTONOMOUS(1, "Autónomo"),
    DGNSS(2, "DGNSS"),
    PPS(3, "PPS"),
    RTK_FIXED(4, "RTK Fijo"),
    RTK_FLOAT(5, "RTK Flotante"),
    ESTIMATED(6, "Estimado"),
    MANUAL(7, "Manual"),
    SIMULATION(8, "Simulación"),
    EXTERNAL_MOCK(9, "Externa (mock)");

    companion object {
        fun fromGga(quality: Int): FixQuality =
            entries.firstOrNull { it.gga == quality } ?: UNKNOWN
    }
}

/**
 * Fuente de posiciones GNSS. Implementaciones: el chip interno del teléfono
 * ([GnssEngine]) o un receptor externo RTK por Bluetooth ([BluetoothNmeaSource]).
 */
interface GnssSource {
    val location: LiveData<Location>
    val satellites: LiveData<SatelliteInfo>
    val fixQuality: LiveData<FixQuality>
    /** Texto de estado de la fuente (conectando, conectado, error…). */
    val status: LiveData<String>

    fun start()
    fun stop()
}
