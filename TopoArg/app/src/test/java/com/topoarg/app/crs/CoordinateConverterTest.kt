package com.topoarg.app.crs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Validación numérica del motor de coordenadas con puntos de control
 * en Argentina (Obelisco de Buenos Aires y centro de Córdoba).
 */
class CoordinateConverterTest {

    // Obelisco, Buenos Aires (WGS84)
    private val obeliscoLat = -34.6037389
    private val obeliscoLon = -58.3815704

    @Test
    fun `catalogo completo - 33 sistemas`() {
        // 4 datums GK x 7 fajas + 4 UTM + 5 geográficos = 37
        assertEquals(37, CrsCatalog.all.size)
        // Sin EPSG duplicados
        assertEquals(CrsCatalog.all.size, CrsCatalog.all.map { it.epsg }.toSet().size)
    }

    @Test
    fun `posgar 2007 faja 5 - obelisco dentro del rango esperado`() {
        val crs = CrsCatalog.byEpsg(5347) // POSGAR 2007 / Faja 5
        val c = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, crs)

        // Y (Este): 5.500.000 + desplazamiento desde el meridiano -60°.
        val expectedEastOffset = (obeliscoLon - (-60.0)) * 111_320.0 *
            cos(Math.toRadians(obeliscoLat))
        assertEquals(5_500_000.0 + expectedEastOffset, c.east, 500.0)

        // X (Norte): arco de meridiano desde el polo sur, ~6.15M m en Buenos Aires.
        assertTrue("Norte fuera de rango: ${c.north}", c.north in 6_140_000.0..6_180_000.0)
    }

    @Test
    fun `posgar 94 y posgar 2007 son practicamente identicos`() {
        // Ambos datums son geocéntricos (towgs84 = 0): la diferencia práctica es nula.
        val p07 = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(5347))
        val p94 = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(22185))
        assertTrue(hypot(p07.north - p94.north, p07.east - p94.east) < 1.0)
    }

    @Test
    fun `campo inchauspe difiere de posgar en el orden de 100-400 m`() {
        val posgar = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(5347))
        val inch = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(22195))
        val d = hypot(posgar.north - inch.north, posgar.east - inch.east)
        assertTrue("Diferencia de datum inesperada: $d m", d in 50.0..500.0)
    }

    @Test
    fun `seleccion automatica de faja`() {
        assertEquals(6, CrsCatalog.fajaForLongitude(-58.38)) // CABA (faja 6: -58.5..-55.5)
        assertEquals(5, CrsCatalog.fajaForLongitude(-59.50)) // Pcia. Bs.As. oeste (faja 5)
        assertEquals(4, CrsCatalog.fajaForLongitude(-64.18)) // Córdoba
        assertEquals(2, CrsCatalog.fajaForLongitude(-68.84)) // Mendoza
        assertEquals(1, CrsCatalog.fajaForLongitude(-73.5))  // extremo oeste
        assertEquals(7, CrsCatalog.fajaForLongitude(-53.6))  // extremo este (Misiones)
        // Fuera de rango: se acota a 1..7
        assertEquals(1, CrsCatalog.fajaForLongitude(-80.0))
        assertEquals(7, CrsCatalog.fajaForLongitude(-40.0))
    }

    @Test
    fun `withFaja mantiene el datum`() {
        val inchauspe3 = CrsCatalog.withFaja(CrsCatalog.byEpsg(22195), 3)
        assertEquals(22193, inchauspe3.epsg)
        val posgar1 = CrsCatalog.withFaja(CrsCatalog.byEpsg(5347), 1)
        assertEquals(5343, posgar1.epsg)
        // Un CRS sin faja no cambia
        val utm = CrsCatalog.byEpsg(32720)
        assertEquals(utm, CrsCatalog.withFaja(utm, 3))
    }

    @Test
    fun `crs efectivo con faja automatica`() {
        val cordobaLon = -64.18
        val efectivo = CrsCatalog.effective(CrsCatalog.byEpsg(5347), cordobaLon, autoFaja = true)
        assertEquals(5346, efectivo.epsg) // POSGAR 2007 / Faja 4
        val fijo = CrsCatalog.effective(CrsCatalog.byEpsg(5347), cordobaLon, autoFaja = false)
        assertEquals(5347, fijo.epsg)
    }

    @Test
    fun `geograficas wgs84 devuelven el mismo valor`() {
        val c = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(4326))
        assertEquals(obeliscoLat, c.north, 1e-9)
        assertEquals(obeliscoLon, c.east, 1e-9)
    }

    @Test
    fun `utm zona 21S - obelisco`() {
        val c = CoordinateConverter.fromWgs84(obeliscoLat, obeliscoLon, CrsCatalog.byEpsg(32721))
        // Valores conocidos aproximados del Obelisco en UTM 21S.
        assertEquals(372_000.0, c.east, 5_000.0)
        assertEquals(6_170_000.0, c.north, 10_000.0)
    }

    @Test
    fun `conversion a todos los sistemas no falla`() {
        val all = CoordinateConverter.toAllSystems(obeliscoLat, obeliscoLon)
        assertEquals(CrsCatalog.all.size, all.size)
        all.forEach {
            assertTrue(it.north.isFinite())
            assertTrue(it.east.isFinite())
        }
    }
}
