package com.topoarg.app.geoid

import android.content.Context
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Modelo de geoide para Argentina basado en una grilla EGM96 (15') recortada
 * al territorio nacional (asset `geoid_egm96_ar.grd`), con interpolación bilineal.
 *
 * Permite obtener la altura ortométrica H (cota s.n.m.) a partir de la altura
 * elipsoidal h del receptor:  H = h − N.
 *
 * Nota de precisión: EGM96 difiere del modelo oficial GEOIDE-Ar 16 del IGN
 * típicamente en menos de ±1 m. Si se dispone de la grilla oficial, puede
 * reemplazarse el asset manteniendo el mismo formato.
 *
 * Formato del asset (texto):
 *   línea 1: latMin latMax lonMin lonMax paso nFilas nColumnas
 *   luego nFilas líneas con nColumnas valores de N (m), latitud ascendente.
 */
object GeoidModel {

    private class Grid(
        val latMin: Double, val latMax: Double,
        val lonMin: Double, val lonMax: Double,
        val step: Double, val rows: Int, val cols: Int,
        val values: FloatArray
    )

    @Volatile
    private var grid: Grid? = null

    val isReady: Boolean get() = grid != null

    /** Carga el asset en un hilo secundario (≈80 KB, casi instantáneo). */
    fun init(context: Context) {
        if (grid != null) return
        val appContext = context.applicationContext
        Thread {
            try {
                appContext.assets.open("geoid_egm96_ar.grd").use { load(it) }
            } catch (_: Exception) {
                // Sin geoide: la app sigue funcionando solo con alturas elipsoidales.
            }
        }.apply { isDaemon = true }.start()
    }

    /** Carga sincrónica desde un stream (usada también por los tests). */
    fun load(input: InputStream) {
        BufferedReader(InputStreamReader(input)).use { reader ->
            val header = reader.readLine().trim().split(Regex("\\s+"))
            val latMin = header[0].toDouble()
            val latMax = header[1].toDouble()
            val lonMin = header[2].toDouble()
            val lonMax = header[3].toDouble()
            val step = header[4].toDouble()
            val rows = header[5].toInt()
            val cols = header[6].toInt()
            val values = FloatArray(rows * cols)
            var idx = 0
            repeat(rows) {
                val parts = reader.readLine().trim().split(Regex("\\s+"))
                for (c in 0 until cols) values[idx++] = parts[c].toFloat()
            }
            grid = Grid(latMin, latMax, lonMin, lonMax, step, rows, cols, values)
        }
    }

    /**
     * Ondulación del geoide N (m) interpolada bilinealmente.
     * Devuelve null si la grilla no está cargada o el punto cae fuera de ella.
     */
    fun undulation(lat: Double, lon: Double): Double? {
        val g = grid ?: return null
        if (lat < g.latMin || lat > g.latMax || lon < g.lonMin || lon > g.lonMax) return null

        val fi = (lat - g.latMin) / g.step
        val fj = (lon - g.lonMin) / g.step
        val i = fi.toInt().coerceAtMost(g.rows - 2)
        val j = fj.toInt().coerceAtMost(g.cols - 2)
        val di = fi - i
        val dj = fj - j

        fun v(r: Int, c: Int) = g.values[r * g.cols + c].toDouble()

        return v(i, j) * (1 - di) * (1 - dj) +
            v(i + 1, j) * di * (1 - dj) +
            v(i, j + 1) * (1 - di) * dj +
            v(i + 1, j + 1) * di * dj
    }

    /** Altura ortométrica H = h − N, o null si no hay geoide disponible. */
    fun orthometric(lat: Double, lon: Double, ellipsoidalHeight: Double): Double? =
        undulation(lat, lon)?.let { ellipsoidalHeight - it }
}
