package com.nexplay.dronepreflight.ui.screens

import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Warstwy Geoportal.gov.pl — WMTS w GoogleMapsCompatible (EPSG:3857),
 * kompatybilne z osmdroid. Darmowe, publiczne, wymagają User-Agent (ustawiony globalnie).
 *
 * Uwaga: Geoportal zwraca kafle w kolejności {z}/{y}/{x} (nie {z}/{x}/{y} jak większość),
 * dlatego nadpisujemy getTileURLString.
 */
object GeoportalTileSources {

    /** Ortofotomapa (zdjęcia lotnicze, HD nad Polską). */
    val ORTHO: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "GeoportalOrto", 1, 19, 256, "",
        arrayOf("https://mapy.geoportal.gov.pl/wss/service/WMTS/guest/wmts/ORTOFOTOMAPA/GoogleMapsCompatible/"),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$z/$y/$x"
        }
    }

    /** Topograficzna mobilna G2 (dobra pod dron — drogi, budynki, teren). */
    val TOPO: OnlineTileSourceBase = object : OnlineTileSourceBase(
        "GeoportalTopo", 1, 19, 256, "",
        arrayOf("https://mapy.geoportal.gov.pl/wss/service/WMTS/guest/wmts/G2_MOBILE_500/GoogleMapsCompatible/"),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return "${baseUrl}$z/$y/$x"
        }
    }
}
