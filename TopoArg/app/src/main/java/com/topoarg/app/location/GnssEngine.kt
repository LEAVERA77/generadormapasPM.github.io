package com.topoarg.app.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

data class SatelliteInfo(val usedInFix: Int, val visible: Int)

/**
 * Motor GNSS: entrega posiciones a 1 Hz con máxima precisión y el estado
 * de la constelación de satélites.
 *
 * Quien lo use es responsable de verificar el permiso ACCESS_FINE_LOCATION
 * antes de llamar a [start].
 */
class GnssEngine(context: Context) {

    private val appContext = context.applicationContext
    private val fused: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _location = MutableLiveData<Location>()
    val location: LiveData<Location> get() = _location

    private val _satellites = MutableLiveData(SatelliteInfo(0, 0))
    val satellites: LiveData<SatelliteInfo> get() = _satellites

    private var running = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { _location.value = it }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var used = 0
            for (i in 0 until status.satelliteCount) {
                if (status.usedInFix(i)) used++
            }
            _satellites.value = SatelliteInfo(used, status.satelliteCount)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMinUpdateDistanceMeters(0f)
            .build()
        fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        @Suppress("DEPRECATION")
        locationManager.registerGnssStatusCallback(gnssCallback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        if (!running) return
        running = false
        fused.removeLocationUpdates(locationCallback)
        locationManager.unregisterGnssStatusCallback(gnssCallback)
    }
}
