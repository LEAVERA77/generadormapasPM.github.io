package com.topoarg.app.geoid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

/**
 * Valida la grilla de geoide embebida contra valores EGM96 calculados con
 * PROJ (pyproj) para puntos de control en Argentina.
 */
class GeoidModelTest {

    @Before
    fun load() {
        // El working directory de los unit tests es el módulo app/ (o la raíz del proyecto).
        val candidates = listOf(
            File("src/main/assets/geoid_egm96_ar.grd"),
            File("app/src/main/assets/geoid_egm96_ar.grd")
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("No se encontró el asset de geoide")
        FileInputStream(file).use { GeoidModel.load(it) }
    }

    @Test
    fun `ondulacion en buenos aires`() {
        val n = GeoidModel.undulation(-34.6037, -58.3816)
        assertNotNull(n)
        assertEquals(16.137, n!!, 0.15)
    }

    @Test
    fun `ondulacion en cordoba`() {
        val n = GeoidModel.undulation(-31.4201, -64.1888)
        assertNotNull(n)
        assertEquals(25.943, n!!, 0.20)
    }

    @Test
    fun `ondulacion en ushuaia`() {
        val n = GeoidModel.undulation(-54.8019, -68.3030)
        assertNotNull(n)
        assertEquals(13.079, n!!, 0.20)
    }

    @Test
    fun `fuera de la grilla devuelve null`() {
        assertNull(GeoidModel.undulation(10.0, -58.0))     // hemisferio norte
        assertNull(GeoidModel.undulation(-34.6, 10.0))     // África
    }

    @Test
    fun `altura ortometrica = elipsoidal menos ondulacion`() {
        val h = 100.0
        val lat = -34.6037
        val lon = -58.3816
        val n = GeoidModel.undulation(lat, lon)!!
        val orto = GeoidModel.orthometric(lat, lon, h)!!
        assertEquals(h - n, orto, 1e-9)
        // En Buenos Aires la cota debe ser ~16 m menor que la altura elipsoidal.
        assertTrue(orto < h)
    }

    @Test
    fun `bordes de la grilla no fallan`() {
        assertNotNull(GeoidModel.undulation(-56.0, -76.0))
        assertNotNull(GeoidModel.undulation(-21.0, -52.0))
    }
}
