package com.topoarg.app.export

import com.topoarg.app.crs.CoordinateConverter
import com.topoarg.app.crs.CrsDef
import com.topoarg.app.data.SurveyPoint
import com.topoarg.app.geoid.GeoidModel
import com.topoarg.app.location.FixQuality
import java.time.Instant
import java.util.Locale

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    CSV("CSV (planilla)", "csv", "text/csv"),
    KML("KML (Google Earth)", "kml", "application/vnd.google-earth.kml+xml"),
    GPX("GPX (GPS exchange)", "gpx", "application/gpx+xml"),
    GEOJSON("GeoJSON", "geojson", "application/geo+json"),
    DXF("DXF (AutoCAD)", "dxf", "image/vnd.dxf")
}

/**
 * Generadores de archivos de exportación. Los formatos proyectados (CSV, DXF)
 * usan el CRS indicado; KML/GPX/GeoJSON usan WGS84 por especificación.
 */
object Exporters {

    fun generate(format: ExportFormat, points: List<SurveyPoint>, crs: CrsDef): String =
        when (format) {
            ExportFormat.CSV -> csv(points, crs)
            ExportFormat.KML -> kml(points)
            ExportFormat.GPX -> gpx(points)
            ExportFormat.GEOJSON -> geojson(points, crs)
            ExportFormat.DXF -> dxf(points, crs)
        }

    private fun num(v: Double, decimals: Int = 3): String =
        String.format(Locale.US, "%.${decimals}f", v)

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    private fun jsonEsc(s: String): String = s
        .replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

    /** Altura ortométrica si el geoide está disponible, o null. */
    private fun ortho(p: SurveyPoint): Double? =
        GeoidModel.orthometric(p.lat, p.lon, p.altitude)

    /** Cota a usar en formatos de elevación s.n.m. (KML/GPX/DXF). */
    private fun elevation(p: SurveyPoint): Double = ortho(p) ?: p.altitude

    private fun fixLabel(p: SurveyPoint): String = FixQuality.fromGga(p.fixQuality).label

    fun csv(points: List<SurveyPoint>, crs: CrsDef): String {
        val sb = StringBuilder()
        sb.append("Nombre,Descripcion,FechaHora,CRS,")
        if (crs.projected) sb.append("Norte_X_m,Este_Y_m,") else sb.append("Latitud,Longitud,")
        sb.append("Lat_WGS84,Lon_WGS84,h_elipsoidal_m,H_ortometrica_m,")
        sb.append("Precision_m,Muestras,DesvioStd_m,CalidadFix\r\n")
        for (p in points) {
            val c = CoordinateConverter.fromWgs84(p.lat, p.lon, crs)
            sb.append('"').append(p.name.replace("\"", "\"\"")).append("\",")
            sb.append('"').append(p.description.replace("\"", "\"\"")).append("\",")
            sb.append(Instant.ofEpochMilli(p.timestamp)).append(',')
            sb.append(crs.id).append(',')
            if (crs.projected) {
                sb.append(num(c.north)).append(',').append(num(c.east)).append(',')
            } else {
                sb.append(num(c.north, 8)).append(',').append(num(c.east, 8)).append(',')
            }
            sb.append(num(p.lat, 8)).append(',')
            sb.append(num(p.lon, 8)).append(',')
            sb.append(num(p.altitude)).append(',')
            sb.append(ortho(p)?.let { num(it) } ?: "").append(',')
            sb.append(num(p.accuracy.toDouble(), 2)).append(',')
            sb.append(p.samples).append(',')
            sb.append(num(p.stdHorizontal)).append(',')
            sb.append(fixLabel(p)).append("\r\n")
        }
        return sb.toString()
    }

    fun kml(points: List<SurveyPoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n<Document>\n")
        sb.append("  <name>Puntos TopoArg</name>\n")
        for (p in points) {
            sb.append("  <Placemark>\n")
            sb.append("    <name>").append(esc(p.name)).append("</name>\n")
            sb.append("    <description>").append(esc(p.description))
                .append(" | Solución: ").append(fixLabel(p))
                .append(" | Precisión: ").append(num(p.accuracy.toDouble(), 2))
                .append(" m | Muestras: ").append(p.samples)
                .append("</description>\n")
            sb.append("    <Point><altitudeMode>absolute</altitudeMode><coordinates>")
                .append(num(p.lon, 8)).append(',')
                .append(num(p.lat, 8)).append(',')
                .append(num(elevation(p)))
                .append("</coordinates></Point>\n")
            sb.append("  </Placemark>\n")
        }
        sb.append("</Document>\n</kml>\n")
        return sb.toString()
    }

    fun gpx(points: List<SurveyPoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append(
            "<gpx version=\"1.1\" creator=\"TopoArg\" " +
                "xmlns=\"http://www.topografix.com/GPX/1/1\">\n"
        )
        for (p in points) {
            sb.append("  <wpt lat=\"").append(num(p.lat, 8))
                .append("\" lon=\"").append(num(p.lon, 8)).append("\">\n")
            sb.append("    <ele>").append(num(elevation(p))).append("</ele>\n")
            sb.append("    <time>").append(Instant.ofEpochMilli(p.timestamp)).append("</time>\n")
            sb.append("    <name>").append(esc(p.name)).append("</name>\n")
            if (p.description.isNotBlank()) {
                sb.append("    <desc>").append(esc(p.description)).append("</desc>\n")
            }
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    fun geojson(points: List<SurveyPoint>, crs: CrsDef): String {
        val sb = StringBuilder()
        sb.append("{\n  \"type\": \"FeatureCollection\",\n  \"features\": [\n")
        points.forEachIndexed { i, p ->
            val c = CoordinateConverter.fromWgs84(p.lat, p.lon, crs)
            sb.append("    {\n      \"type\": \"Feature\",\n")
            sb.append("      \"geometry\": {\"type\": \"Point\", \"coordinates\": [")
                .append(num(p.lon, 8)).append(", ").append(num(p.lat, 8)).append(", ")
                .append(num(p.altitude)).append("]},\n")
            sb.append("      \"properties\": {\n")
            sb.append("        \"name\": \"").append(jsonEsc(p.name)).append("\",\n")
            sb.append("        \"description\": \"").append(jsonEsc(p.description)).append("\",\n")
            sb.append("        \"timestamp\": \"").append(Instant.ofEpochMilli(p.timestamp)).append("\",\n")
            sb.append("        \"crs\": \"").append(crs.id).append("\",\n")
            if (crs.projected) {
                sb.append("        \"norte_x\": ").append(num(c.north)).append(",\n")
                sb.append("        \"este_y\": ").append(num(c.east)).append(",\n")
            }
            sb.append("        \"h_elipsoidal\": ").append(num(p.altitude)).append(",\n")
            ortho(p)?.let {
                sb.append("        \"h_ortometrica\": ").append(num(it)).append(",\n")
            }
            sb.append("        \"calidad_fix\": \"").append(jsonEsc(fixLabel(p))).append("\",\n")
            sb.append("        \"precision_m\": ").append(num(p.accuracy.toDouble(), 2)).append(",\n")
            sb.append("        \"muestras\": ").append(p.samples).append("\n")
            sb.append("      }\n    }")
            sb.append(if (i < points.size - 1) ",\n" else "\n")
        }
        sb.append("  ]\n}\n")
        return sb.toString()
    }

    /**
     * DXF R12 mínimo: un POINT y un TEXT por punto, en coordenadas del CRS
     * proyectado elegido (Este = X CAD, Norte = Y CAD, convención de dibujo).
     * La Z es la cota s.n.m. si el geoide está disponible.
     */
    fun dxf(points: List<SurveyPoint>, crs: CrsDef): String {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nENTITIES\n")
        for (p in points) {
            val c = CoordinateConverter.fromWgs84(p.lat, p.lon, crs)
            val x = if (crs.projected) c.east else p.lon
            val y = if (crs.projected) c.north else p.lat
            sb.append("0\nPOINT\n8\nPUNTOS\n")
            sb.append("10\n").append(num(x)).append('\n')
            sb.append("20\n").append(num(y)).append('\n')
            sb.append("30\n").append(num(elevation(p))).append('\n')
            sb.append("0\nTEXT\n8\nETIQUETAS\n")
            sb.append("10\n").append(num(x + 0.5)).append('\n')
            sb.append("20\n").append(num(y + 0.5)).append('\n')
            sb.append("30\n0.0\n")
            sb.append("40\n0.5\n")
            sb.append("1\n").append(p.name.replace("\n", " ")).append('\n')
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }
}
