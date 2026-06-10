package com.topoarg.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object Format {

    /** Grados, minutos y segundos con hemisferio (ej: 34° 36' 12.345" S). */
    fun dms(value: Double, isLatitude: Boolean): String {
        val hemi = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "O"
        }
        val a = abs(value)
        val d = a.toInt()
        val mFull = (a - d) * 60.0
        val m = mFull.toInt()
        val s = (mFull - m) * 60.0
        return String.format(Locale.US, "%d° %02d' %06.3f\" %s", d, m, s, hemi)
    }

    /** Grados decimales con 8 decimales (~1 mm). */
    fun deg(value: Double): String = String.format(Locale.US, "%.8f°", value)

    /** Metros con 3 decimales. */
    fun m(value: Double): String = String.format(Locale.US, "%,.3f m", value)

    /** Metros con 2 decimales, para precisiones. */
    fun acc(value: Float): String = String.format(Locale.US, "%.2f m", value)

    fun dateTime(timestamp: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
