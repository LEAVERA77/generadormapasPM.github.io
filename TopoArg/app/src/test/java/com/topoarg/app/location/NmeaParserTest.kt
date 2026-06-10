package com.topoarg.app.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NmeaParserTest {

    // Ejemplo clásico de la especificación NMEA (checksum válido *47).
    private val ggaNorth = "\$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,46.9,M,,*47"

    // Sentencia tipo receptor RTK en Buenos Aires (sin checksum), fix RTK fijo (4).
    private val ggaRtk =
        "\$GNGGA,140000.00,3436.2243,S,05822.8942,W,4,18,0.7,25.4,M,16.1,M,1.0,0000"

    private val gst = "\$GPGST,182141.000,15.5,15.3,7.2,21.8,0.9,0.5,0.8"

    @Test
    fun `checksum valido`() {
        assertTrue(NmeaParser.checksumOk(ggaNorth))
        // Checksum adulterado
        assertFalse(NmeaParser.checksumOk(ggaNorth.dropLast(2) + "00"))
        // Sin checksum: se acepta
        assertTrue(NmeaParser.checksumOk(ggaRtk))
    }

    @Test
    fun `gga hemisferio norte`() {
        val gga = NmeaParser.parseGga(ggaNorth)
        assertNotNull(gga)
        assertEquals(48.1173, gga!!.lat, 1e-4)
        assertEquals(11.5166667, gga.lon, 1e-4)
        assertEquals(1, gga.quality)
        assertEquals(8, gga.satellites)
        assertEquals(0.9, gga.hdop!!, 1e-9)
        // h elipsoidal = MSL + separación del geoide
        assertEquals(545.4 + 46.9, gga.ellipsoidalHeight, 1e-9)
        assertEquals(545.4, gga.orthometricHeight, 1e-9)
    }

    @Test
    fun `gga rtk fijo en hemisferio sur`() {
        val gga = NmeaParser.parseGga(ggaRtk)
        assertNotNull(gga)
        assertEquals(-(34.0 + 36.2243 / 60.0), gga!!.lat, 1e-7)
        assertEquals(-(58.0 + 22.8942 / 60.0), gga.lon, 1e-7)
        assertEquals(4, gga.quality) // RTK fijo
        assertEquals(FixQuality.RTK_FIXED, FixQuality.fromGga(gga.quality))
        assertEquals(18, gga.satellites)
        assertEquals(25.4 + 16.1, gga.ellipsoidalHeight, 1e-9)
    }

    @Test
    fun `gga sin fix devuelve null`() {
        val sinFix = "\$GPGGA,123519,,,,,0,00,,,M,,M,,"
        assertNull(NmeaParser.parseGga(sinFix))
    }

    @Test
    fun `gst desviacion horizontal`() {
        assertTrue(NmeaParser.isGst(gst))
        val acc = NmeaParser.parseGstHorizontal(gst)!!
        // sqrt(0.9² + 0.5²) ≈ 1.0296
        assertEquals(1.0296f, acc, 1e-3f)
    }

    @Test
    fun `deteccion de tipo de sentencia con cualquier talker`() {
        assertTrue(NmeaParser.isGga(ggaNorth))
        assertTrue(NmeaParser.isGga(ggaRtk)) // talker GN
        assertFalse(NmeaParser.isGga(gst))
        assertFalse(NmeaParser.isGst(ggaNorth))
        assertFalse(NmeaParser.isGga("no es nmea"))
    }

    @Test
    fun `coordenada ddmm a grados decimales`() {
        assertEquals(-34.6037383, NmeaParser.parseCoordinate("3436.2243", "S")!!, 1e-6)
        assertEquals(48.1173, NmeaParser.parseCoordinate("4807.038", "N")!!, 1e-6)
        assertNull(NmeaParser.parseCoordinate("", "N"))
        assertNull(NmeaParser.parseCoordinate("abc", "N"))
    }

    @Test
    fun `calidades de fix mapeadas`() {
        assertEquals(FixQuality.AUTONOMOUS, FixQuality.fromGga(1))
        assertEquals(FixQuality.DGNSS, FixQuality.fromGga(2))
        assertEquals(FixQuality.RTK_FIXED, FixQuality.fromGga(4))
        assertEquals(FixQuality.RTK_FLOAT, FixQuality.fromGga(5))
        assertEquals(FixQuality.UNKNOWN, FixQuality.fromGga(99))
    }
}
