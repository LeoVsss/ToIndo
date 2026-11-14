package br.com.toindo.data

import android.content.Context
import kotlin.math.cos
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox

object MapaCache {

    private const val ZOOM_MIN = 13
    private const val ZOOM_MAX = 17

    fun baixar(context: Context, latitude: Double, longitude: Double, raioM: Int, aoTerminar: (Boolean) -> Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        val raio = raioM.coerceIn(300, 2000) + 300.0
        val dLat = raio / 111_320.0
        val dLon = raio / (111_320.0 * cos(Math.toRadians(latitude)))
        val area = BoundingBox(latitude + dLat, longitude + dLon, latitude - dLat, longitude - dLon)
        val gerente = CacheManager(TileSourceFactory.MAPNIK, SqlTileWriter(), ZOOM_MIN, ZOOM_MAX)
        gerente.downloadAreaAsyncNoUI(
            context.applicationContext, area, ZOOM_MIN, ZOOM_MAX,
            object : CacheManager.CacheManagerCallback {
                override fun onTaskComplete() = aoTerminar(true)
                override fun onTaskFailed(errors: Int) = aoTerminar(false)
                override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
                override fun downloadStarted() = Unit
                override fun setPossibleTilesInArea(total: Int) = Unit
            },
        )
    }
}