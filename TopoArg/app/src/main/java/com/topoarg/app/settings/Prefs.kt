package com.topoarg.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.topoarg.app.crs.CrsCatalog
import com.topoarg.app.crs.CrsDef

object Prefs {

    private const val KEY_CRS = "crs_epsg"
    private const val KEY_AUTO_FAJA = "auto_faja"
    private const val KEY_SAMPLES = "avg_samples"
    private const val KEY_MAX_ACC = "max_accuracy"

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("topoarg_prefs", Context.MODE_PRIVATE)
    }

    /** CRS de trabajo seleccionado. */
    var crs: CrsDef
        get() = CrsCatalog.byEpsg(sp.getInt(KEY_CRS, CrsCatalog.default.epsg))
        set(value) = sp.edit().putInt(KEY_CRS, value.epsg).apply()

    /** Selección automática de faja Gauss-Krüger según la longitud actual. */
    var autoFaja: Boolean
        get() = sp.getBoolean(KEY_AUTO_FAJA, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_FAJA, value).apply()

    /** Cantidad de épocas a promediar. */
    var averagingSamples: Int
        get() = sp.getInt(KEY_SAMPLES, 30)
        set(value) = sp.edit().putInt(KEY_SAMPLES, value.coerceIn(2, 10_000)).apply()

    /** Precisión horizontal máxima aceptada (m) para incorporar una época al promedio. */
    var maxAccuracy: Float
        get() = sp.getFloat(KEY_MAX_ACC, 10f)
        set(value) = sp.edit().putFloat(KEY_MAX_ACC, value.coerceIn(0.1f, 500f)).apply()
}
