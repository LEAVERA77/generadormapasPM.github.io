package com.topoarg.app.location

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.UUID

/**
 * Fuente GNSS desde un receptor externo (RTK, geodésico o navegador) conectado
 * por Bluetooth clásico (perfil serie SPP) que emita NMEA 0183: Emlid Reach,
 * South, ComNav, Stonex, Garmin GLO, etc.
 *
 * La precisión se toma de la sentencia GST si el receptor la emite (lo usual
 * en equipos RTK); si no, se estima a partir del HDOP de la GGA.
 *
 * Quien la use debe verificar el permiso BLUETOOTH_CONNECT (API 31+) antes
 * de llamar a [start].
 */
class BluetoothNmeaSource(
    context: Context,
    private val deviceAddress: String
) : GnssSource {

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        /** Error de usuario equivalente aproximado para estimar precisión desde HDOP. */
        private const val UERE_METERS = 3.0
    }

    private val appContext = context.applicationContext

    private val _location = MutableLiveData<Location>()
    override val location: LiveData<Location> get() = _location

    private val _satellites = MutableLiveData(SatelliteInfo(0, 0))
    override val satellites: LiveData<SatelliteInfo> get() = _satellites

    private val _fixQuality = MutableLiveData(FixQuality.UNKNOWN)
    override val fixQuality: LiveData<FixQuality> get() = _fixQuality

    private val _status = MutableLiveData("")
    override val status: LiveData<String> get() = _status

    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var socket: BluetoothSocket? = null

    /** Última precisión horizontal reportada por GST (m). */
    @Volatile
    private var gstAccuracy: Float? = null

    @SuppressLint("MissingPermission")
    override fun start() {
        if (running) return
        running = true
        _status.value = "Conectando al receptor…"
        thread = Thread {
            try {
                val manager =
                    appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val adapter = manager.adapter
                    ?: throw IOException("Este equipo no tiene Bluetooth")
                if (!adapter.isEnabled) throw IOException("Bluetooth desactivado")
                val device = adapter.getRemoteDevice(deviceAddress)
                adapter.cancelDiscovery()
                val sock = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = sock
                sock.connect()
                _status.postValue("Conectado: ${device.name ?: deviceAddress}")

                val reader = BufferedReader(InputStreamReader(sock.inputStream))
                while (running) {
                    val line = reader.readLine() ?: break
                    handleSentence(line.trim())
                }
                if (running) _status.postValue("Receptor desconectado")
            } catch (e: Exception) {
                if (running) _status.postValue("Error Bluetooth: ${e.message}")
            } finally {
                closeSocket()
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    override fun stop() {
        running = false
        closeSocket()
        thread = null
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
    }

    private fun handleSentence(line: String) {
        if (!line.startsWith("$")) return
        if (line.contains('*') && !NmeaParser.checksumOk(line)) return

        when {
            NmeaParser.isGst(line) -> {
                NmeaParser.parseGstHorizontal(line)?.let { gstAccuracy = it }
            }
            NmeaParser.isGga(line) -> {
                val gga = NmeaParser.parseGga(line) ?: return
                val loc = Location("bluetooth_nmea").apply {
                    latitude = gga.lat
                    longitude = gga.lon
                    altitude = gga.ellipsoidalHeight
                    accuracy = gstAccuracy
                        ?: ((gga.hdop ?: 1.0) * UERE_METERS).toFloat()
                    time = System.currentTimeMillis()
                }
                _location.postValue(loc)
                _fixQuality.postValue(FixQuality.fromGga(gga.quality))
                _satellites.postValue(SatelliteInfo(gga.satellites, gga.satellites))
            }
        }
    }
}
