package com.lecturasgas.beta.map

import com.lecturasgas.beta.data.model.TrackPoint
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * Todo lo que se dibuja en el mapa, en capas (como Google Maps):
 *
 *   satélite (opcional)
 *   ruta recomendada  - - - -  (naranja punteada)
 *   recorrido real    ───────  (morado)
 *   grupos de puntos  (círculos con número cuando hay muchos juntos)
 *   medidores         ●  (color según estado)
 *   NIR               (texto, solo con zoom cercano)
 *   seleccionado      ◯  (anillo)
 *
 * Usar capas en vez de un "Marker" por medidor es MUCHO más liviano:
 * el mapa dibuja 2000 puntos sin trabarse en celulares sencillos.
 */
object MapLayers {

    const val STREETS_STYLE = "https://tiles.openfreemap.org/styles/liberty"
    /** Mapa oscuro para el modo oscuro de la app. */
    const val DARK_STYLE = "https://tiles.openfreemap.org/styles/dark"

    /** Estilo mínimo para cuando no hay internet: fondo liso + nuestros puntos. */
    const val OFFLINE_STYLE = """{
      "version": 8,
      "name": "sin-conexion",
      "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
      "sources": {},
      "layers": [ { "id": "fondo", "type": "background", "paint": { "background-color": "#EEF1EC" } } ]
    }"""

    private const val SATELLITE_URL =
        "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    private const val SATELLITE_NATIVE_MAX_ZOOM = 17f

    const val SRC_METERS = "lg_meters"
    const val SRC_SELECTED = "lg_selected"
    const val SRC_TRACK = "lg_track"
    const val SRC_PLAN = "lg_plan"
    const val SRC_ORDER_LINE = "lg_order_line"
    const val SRC_ORDER_POINTS = "lg_order_points"
    const val SRC_DOUBTFUL = "lg_doubtful"
    private const val SRC_SATELLITE = "lg_satellite"

    const val LAYER_SATELLITE = "lg_satellite_layer"
    const val LAYER_PLAN = "lg_plan_layer"
    const val LAYER_ORDER_LINE = "lg_order_line_layer"
    const val LAYER_ORDER_DOTS = "lg_order_dots"
    const val LAYER_ORDER_ARROWS = "lg_order_arrows"
    const val LAYER_ORDER_NUMBERS = "lg_order_numbers"
    const val LAYER_TRACK = "lg_track_layer"
    const val LAYER_CLUSTERS = "lg_clusters"
    const val LAYER_CLUSTER_COUNT = "lg_cluster_count"
    const val LAYER_POINTS = "lg_points"
    const val LAYER_LABELS = "lg_labels"
    const val LAYER_DOUBTFUL = "lg_doubtful_ring"
    const val LAYER_SELECTED = "lg_selected_layer"

    private val FONT = arrayOf("Noto Sans Regular")

    fun install(style: Style, satellite: Boolean) {
        if (style.getSource(SRC_SATELLITE) == null) {
            val tiles = TileSet("2.2.0", SATELLITE_URL).apply {
                scheme = "xyz"
                minZoom = 0f
                // Las fotos de ArcGIS solo existen hasta este zoom en pueblos pequeños.
                // Más cerca, el mapa AGRANDA la última foto en vez de pedir una que no
                // existe (eso causaba el cartel "Map data not yet available").
                // Si algún día ves nítido con más zoom, sube este número (máx. 19).
                maxZoom = SATELLITE_NATIVE_MAX_ZOOM
            }
            style.addSource(RasterSource(SRC_SATELLITE, tiles, 256))
            style.addLayer(
                RasterLayer(LAYER_SATELLITE, SRC_SATELLITE).withProperties(
                    PropertyFactory.visibility(if (satellite) Property.VISIBLE else Property.NONE)
                )
            )
        }
        if (style.getSource(SRC_METERS) != null) return

        val empty = FeatureCollection.fromFeatures(emptyList<Feature>())
        style.addSource(
            GeoJsonSource(
                SRC_METERS, empty,
                GeoJsonOptions().withCluster(true).withClusterMaxZoom(15).withClusterRadius(42)
            )
        )
        style.addSource(GeoJsonSource(SRC_SELECTED, empty, GeoJsonOptions()))
        style.addSource(GeoJsonSource(SRC_TRACK, empty, GeoJsonOptions()))
        style.addSource(GeoJsonSource(SRC_PLAN, empty, GeoJsonOptions()))
        style.addSource(GeoJsonSource(SRC_ORDER_LINE, empty, GeoJsonOptions()))
        style.addSource(GeoJsonSource(SRC_ORDER_POINTS, empty, GeoJsonOptions()))
        style.addSource(GeoJsonSource(SRC_DOUBTFUL, empty, GeoJsonOptions()))

        style.addLayer(
            LineLayer(LAYER_PLAN, SRC_PLAN).withProperties(
                PropertyFactory.lineColor("#F28C28"),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineDasharray(arrayOf(2f, 1.6f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineOpacity(0.9f)
            )
        )
        style.addLayer(
            LineLayer(LAYER_TRACK, SRC_TRACK).withProperties(
                PropertyFactory.lineColor("#546E7A"),
                PropertyFactory.lineWidth(4.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.8f)
            )
        )
        style.addLayer(
            CircleLayer(LAYER_CLUSTERS, SRC_METERS)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    PropertyFactory.circleColor("#0B4F8A"),
                    PropertyFactory.circleRadius(
                        Expression.step(
                            Expression.get("point_count"),
                            Expression.literal(15f),
                            Expression.stop(25, 19f),
                            Expression.stop(100, 24f),
                            Expression.stop(400, 30f)
                        )
                    ),
                    PropertyFactory.circleOpacity(0.88f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f)
                )
        )
        style.addLayer(
            SymbolLayer(LAYER_CLUSTER_COUNT, SRC_METERS)
                .withFilter(Expression.has("point_count"))
                .withProperties(
                    PropertyFactory.textField(Expression.get("point_count_abbreviated")),
                    PropertyFactory.textFont(FONT),
                    PropertyFactory.textSize(13f),
                    PropertyFactory.textColor("#FFFFFF"),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
        )
        style.addLayer(
            CircleLayer(LAYER_POINTS, SRC_METERS)
                .withFilter(Expression.not(Expression.has("point_count")))
                .withProperties(
                    PropertyFactory.circleColor(Expression.toColor(Expression.get("c"))),
                    PropertyFactory.circleRadius(Expression.get("r")),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2f)
                )
        )
        // Aro rojo alrededor de los puntos que parecen estar mal ubicados.
        style.addLayer(
            CircleLayer(LAYER_DOUBTFUL, SRC_DOUBTFUL).withProperties(
                PropertyFactory.circleRadius(15f),
                PropertyFactory.circleOpacity(0f),
                PropertyFactory.circleStrokeColor("#D32F2F"),
                PropertyFactory.circleStrokeWidth(3f),
                PropertyFactory.circleStrokeOpacity(0.95f)
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SRC_METERS)
                .withFilter(Expression.not(Expression.has("point_count")))
                .withProperties(
                    PropertyFactory.textField(Expression.get("nir")),
                    PropertyFactory.textFont(FONT),
                    PropertyFactory.textSize(11.5f),
                    PropertyFactory.textOffset(arrayOf(0f, 1.25f)),
                    PropertyFactory.textColor("#073763"),
                    PropertyFactory.textHaloColor("#FFFFFF"),
                    PropertyFactory.textHaloWidth(1.6f)
                )
                .apply { minZoom = 16.5f }
        )
        // Orden en que registraste las lecturas: línea verde azulada + número en cada punto.
        style.addLayer(
            LineLayer(LAYER_ORDER_LINE, SRC_ORDER_LINE).withProperties(
                PropertyFactory.lineColor("#00897B"),
                PropertyFactory.lineWidth(3.5f),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                PropertyFactory.lineOpacity(0.9f)
            )
        )
        // Flechas sobre la línea: muestran hacia dónde fuiste (de la lectura 1 a la última).
        style.addLayer(
            SymbolLayer(LAYER_ORDER_ARROWS, SRC_ORDER_LINE).withProperties(
                PropertyFactory.symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
                PropertyFactory.symbolSpacing(70f),
                PropertyFactory.textField(">"),
                PropertyFactory.textFont(FONT),
                PropertyFactory.textSize(17f),
                PropertyFactory.textColor("#00695C"),
                PropertyFactory.textHaloColor("#FFFFFF"),
                PropertyFactory.textHaloWidth(1.6f),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true),
                PropertyFactory.textKeepUpright(false)
            )
        )
        style.addLayer(
            CircleLayer(LAYER_ORDER_DOTS, SRC_ORDER_POINTS).withProperties(
                PropertyFactory.circleColor("#00897B"),
                PropertyFactory.circleRadius(Expression.interpolate(Expression.linear(), Expression.zoom(),
                    Expression.stop(13, 5f), Expression.stop(17, 10f))),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f)
            )
        )
        style.addLayer(
            SymbolLayer(LAYER_ORDER_NUMBERS, SRC_ORDER_POINTS).withProperties(
                PropertyFactory.textField(Expression.get("n")),
                PropertyFactory.textFont(FONT),
                PropertyFactory.textSize(11f),
                PropertyFactory.textColor("#FFFFFF"),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            ).apply { minZoom = 15.5f }
        )
        style.addLayer(
            CircleLayer(LAYER_SELECTED, SRC_SELECTED).withProperties(
                PropertyFactory.circleRadius(17f),
                PropertyFactory.circleOpacity(0f),
                PropertyFactory.circleStrokeColor("#F28C28"),
                PropertyFactory.circleStrokeWidth(4.5f)
            )
        )
    }

    fun setSatellite(style: Style, visible: Boolean) {
        style.getLayer(LAYER_SATELLITE)?.setProperties(
            PropertyFactory.visibility(if (visible) Property.VISIBLE else Property.NONE)
        )
    }

    fun pointsCollection(points: List<MapPoint>): FeatureCollection {
        val features = ArrayList<Feature>(points.size)
        for (p in points) {
            val f = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))
            f.addNumberProperty("row", p.row)
            f.addStringProperty("c", p.color)
            f.addNumberProperty("r", p.radius)
            f.addStringProperty("nir", p.nir)
            features.add(f)
        }
        return FeatureCollection.fromFeatures(features)
    }

    fun plainPoints(points: List<Pair<Double, Double>>): FeatureCollection =
        FeatureCollection.fromFeatures(points.map { Feature.fromGeometry(Point.fromLngLat(it.second, it.first)) })

    fun singlePoint(lat: Double?, lon: Double?): FeatureCollection =
        if (lat == null || lon == null) FeatureCollection.fromFeatures(emptyList<Feature>())
        else FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(Point.fromLngLat(lon, lat))))

    fun line(points: List<Pair<Double, Double>>): FeatureCollection {
        if (points.size < 2) return FeatureCollection.fromFeatures(emptyList<Feature>())
        val line = LineString.fromLngLats(points.map { Point.fromLngLat(it.second, it.first) })
        return FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(line)))
    }

    /** Puntos numerados del orden de lecturas. */
    fun orderPoints(points: List<com.lecturasgas.beta.readings.OrderPoint>): FeatureCollection {
        val features = ArrayList<Feature>(points.size)
        for (p in points) {
            val f = Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat))
            f.addStringProperty("n", p.n.toString())
            features.add(f)
        }
        return FeatureCollection.fromFeatures(features)
    }

    /** El recorrido se corta en tramos cuando hay una pausa larga (más de 10 min entre puntos). */
    fun trackLine(track: List<TrackPoint>): FeatureCollection {
        val features = ArrayList<Feature>()
        var seg = ArrayList<Point>()
        var lastTime = 0L
        for (t in track) {
            if (seg.isNotEmpty() && t.time - lastTime > 10 * 60_000L) {
                if (seg.size >= 2) features.add(Feature.fromGeometry(LineString.fromLngLats(seg)))
                seg = ArrayList()
            }
            seg.add(Point.fromLngLat(t.lon, t.lat))
            lastTime = t.time
        }
        if (seg.size >= 2) features.add(Feature.fromGeometry(LineString.fromLngLats(seg)))
        return FeatureCollection.fromFeatures(features)
    }

    fun setData(style: Style, sourceId: String, data: FeatureCollection) {
        (style.getSource(sourceId) as? GeoJsonSource)?.setGeoJson(data)
    }
}
