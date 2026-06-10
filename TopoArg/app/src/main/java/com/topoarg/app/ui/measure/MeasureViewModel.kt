package com.topoarg.app.ui.measure

import android.app.Application
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topoarg.app.location.BluetoothNmeaSource
import com.topoarg.app.location.GnssEngine
import com.topoarg.app.location.GnssSource
import com.topoarg.app.settings.Prefs
import kotlin.math.cos
import kotlin.math.sqrt

/** Estado del promediado en curso. */
data class AveragingState(
    val count: Int,
    val target: Int,
    val meanLat: Double,
    val meanLon: Double,
    val meanAlt: Double,
    /** RMS horizontal de las épocas respecto de la media (m). */
    val stdHorizontal: Double,
    val bestAccuracy: Float,
    val rejected: Int
)

/** Resultado final de un promediado, listo para guardar. */
data class AveragedResult(
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val accuracy: Float,
    val verticalAccuracy: Float,
    val samples: Int,
    val stdHorizontal: Double
)

private const val METERS_PER_DEG = 111_320.0

class MeasureViewModel(app: Application) : AndroidViewModel(app) {

    var source: GnssSource = createSource()
        private set
    private var sourceKey: String = currentSourceKey()

    private fun currentSourceKey() = "${Prefs.gnssSource}:${Prefs.btDeviceAddress}"

    private fun createSource(): GnssSource =
        if (Prefs.gnssSource == Prefs.SOURCE_BLUETOOTH && Prefs.btDeviceAddress.isNotEmpty()) {
            BluetoothNmeaSource(getApplication(), Prefs.btDeviceAddress)
        } else {
            GnssEngine(getApplication())
        }

    /** Recrea la fuente si cambió la configuración (interno ↔ Bluetooth). */
    fun ensureSource(): GnssSource {
        val key = currentSourceKey()
        if (key != sourceKey) {
            source.stop()
            source = createSource()
            sourceKey = key
        }
        return source
    }

    private val _averaging = MutableLiveData<AveragingState?>(null)
    val averaging: LiveData<AveragingState?> get() = _averaging

    /** Evento de un solo disparo: promediado terminado, hay que ofrecer guardar. */
    private val _finished = MutableLiveData<AveragedResult?>(null)
    val finished: LiveData<AveragedResult?> get() = _finished

    private val lats = ArrayList<Double>()
    private val lons = ArrayList<Double>()
    private val alts = ArrayList<Double>()
    private val vAccs = ArrayList<Float>()
    private var bestAcc = Float.MAX_VALUE
    private var rejected = 0
    private var target = 0
    private var active = false

    val isAveraging: Boolean get() = active

    fun startAveraging() {
        lats.clear(); lons.clear(); alts.clear(); vAccs.clear()
        bestAcc = Float.MAX_VALUE
        rejected = 0
        target = Prefs.averagingSamples
        active = true
        _averaging.value = AveragingState(0, target, 0.0, 0.0, 0.0, 0.0, 0f, 0)
    }

    fun cancelAveraging() {
        active = false
        _averaging.value = null
    }

    fun consumeFinished() {
        _finished.value = null
    }

    /** Llamado por el fragment cada vez que llega una posición nueva. */
    fun onLocation(loc: Location) {
        if (!active) return
        if (loc.accuracy > Prefs.maxAccuracy) {
            rejected++
            _averaging.value = _averaging.value?.copy(rejected = rejected)
            return
        }
        lats.add(loc.latitude)
        lons.add(loc.longitude)
        alts.add(loc.altitude)
        if (loc.hasVerticalAccuracy()) vAccs.add(loc.verticalAccuracyMeters)
        if (loc.accuracy < bestAcc) bestAcc = loc.accuracy

        val state = computeState()
        _averaging.value = state

        if (lats.size >= target) {
            active = false
            _averaging.value = null
            _finished.value = AveragedResult(
                lat = state.meanLat,
                lon = state.meanLon,
                alt = state.meanAlt,
                accuracy = bestAcc,
                verticalAccuracy = if (vAccs.isEmpty()) 0f else vAccs.average().toFloat(),
                samples = lats.size,
                stdHorizontal = state.stdHorizontal
            )
        }
    }

    private fun computeState(): AveragingState {
        val n = lats.size
        val meanLat = lats.average()
        val meanLon = lons.average()
        val meanAlt = alts.average()
        var sumSq = 0.0
        val cosLat = cos(Math.toRadians(meanLat))
        for (i in 0 until n) {
            val dN = (lats[i] - meanLat) * METERS_PER_DEG
            val dE = (lons[i] - meanLon) * METERS_PER_DEG * cosLat
            sumSq += dN * dN + dE * dE
        }
        val std = if (n > 1) sqrt(sumSq / n) else 0.0
        return AveragingState(n, target, meanLat, meanLon, meanAlt, std, bestAcc, rejected)
    }

    override fun onCleared() {
        source.stop()
        super.onCleared()
    }
}
