/*
 * GPXParser.java
 * 
 * Copyright (c) 2012, AlternativeVision. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301  USA
 */
package net.federicomatera.agpxp

import androidx.annotation.VisibleForTesting
import net.federicomatera.agpxp.models.*
import net.federicomatera.agpxp.types.FixType
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.SAXException
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

class GpxParser {

    private val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    ).onEach { it.timeZone = TimeZone.getTimeZone("UTC") }

    @Throws(
        ParserConfigurationException::class,
        SAXException::class,
        IOException::class
    )
    fun parse(stream: InputStream): Gpx {
        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(stream)

        var version: String? = null
        var creator: String? = null
        var metadata: GpxMetadata? = null
        val wayPoints = mutableListOf<WayPoint>()
        val tracks = mutableListOf<Track>()
        val routes = mutableListOf<Route>()

        val firstChild = doc.firstChild
        if (firstChild == null || GPX_NODE != firstChild.nodeName) {
            throw IOException("Invalid GPX file")
        }

        val attrs = firstChild.attributes
        for (idx in 0 until attrs.length) {
            val attr = attrs.item(idx)
            when (attr.nodeName) {
                VERSION_ATTR -> version = attr.nodeValue
                CREATOR_ATTR -> creator = attr.nodeValue
            }
        }

        val nodes = firstChild.childNodes
        for (idx in 0 until nodes.length) {
            val currentNode = nodes.item(idx)
            when (currentNode.nodeName) {
                METADATA_NODE -> metadata = parseMetaData(currentNode)
                WPT_NODE -> wayPoints.add(parseWayPoint(currentNode))
                TRK_NODE -> tracks.add(parseTrack(currentNode))
                RTE_NODE -> routes.add(parseRoute(currentNode))
            }
        }

        return Gpx(
            creator = creator,
            version = version,
            metadata = metadata,
            wayPoints = wayPoints,
            tracks = tracks,
            routes = routes
        )
    }

    private fun parseMetaData(node: Node): GpxMetadata? {
        var link: Link? = null
        var time: Date? = null

        val nodes = node.childNodes
        if(nodes != null) {
            for (idx in 0 until nodes.length) {
                val currentNode = nodes.item(idx)
                when(currentNode.nodeName) {
                    LINK_NODE -> {
                        val linkElement = currentNode as Element
                        val href = linkElement.getAttribute("href")

                        val text = linkElement
                            .getElementsByTagName("text")
                            .item(0)
                            ?.textContent
                            ?.trim()

                        link = Link(href, text)
                    }
                    TIME_NODE -> time = parseGpxTime(currentNode.firstChild.nodeValue)
                }
            }
        }

        return GpxMetadata(link, time)
    }

    private fun parseTrack(node: Node): Track {
        val trackPoints = mutableListOf<WayPoint>()
        var name: String? = null
        var comment: String? = null
        var description: String? = null
        var src: String? = null
        var number: Int? = null
        var type: String? = null

        val nodes = node.childNodes
        if (nodes != null) {
            for (idx in 0 until nodes.length) {
                val currentNode = nodes.item(idx)
                when (currentNode.nodeName) {
                    NAME_NODE -> name = currentNode.firstChild.nodeValue
                    CMT_NODE -> comment = currentNode.firstChild.nodeValue
                    DESC_NODE -> description = currentNode.firstChild.nodeValue
                    SRC_NODE -> src = currentNode.firstChild.nodeValue
                    NUMBER_NODE -> number = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toIntOrNull()
                    TYPE_NODE -> type = currentNode.firstChild.nodeValue
                    TRKSEG_NODE -> trackPoints.addAll(parseTrackSeg(currentNode))
                }
            }
        }
        return Track(
            trackPoints = trackPoints,
            name = name,
            comment = comment,
            description = description,
            src = src,
            number = number,
            type = type
        )
    }

    private fun parseRoute(node: Node): Route {
        val routePoints = mutableListOf<WayPoint>()
        var name: String? = null
        var comment: String? = null
        var description: String? = null
        var src: String? = null
        var number: Int? = null
        var type: String? = null

        val nodes = node.childNodes
        if (nodes != null) {
            for (idx in 0 until nodes.length) {
                val currentNode = nodes.item(idx)
                when (currentNode.nodeName) {
                    NAME_NODE -> name = currentNode.firstChild.nodeValue
                    CMT_NODE -> comment = currentNode.firstChild.nodeValue
                    DESC_NODE -> description = currentNode.firstChild.nodeValue
                    SRC_NODE -> src = currentNode.firstChild.nodeValue
                    NUMBER_NODE -> number = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toIntOrNull()
                    TYPE_NODE -> type = currentNode.firstChild.nodeValue
                    RTEPT_NODE -> routePoints.add(parseWayPoint(currentNode))
                }
            }
        }
        return Route(
            routePoints = routePoints,
            name = name,
            comment = comment,
            description = description,
            src = src,
            number = number,
            type = type
        )
    }

    private fun parseTrackSeg(node: Node): List<WayPoint> {
        val trackPoints = mutableListOf<WayPoint>()
        val nodes = node.childNodes
        if (nodes != null) {
            for (idx in 0 until nodes.length) {
                val currentNode = nodes.item(idx)
                if (TRKPT_NODE == currentNode.nodeName) {
                    trackPoints.add(parseWayPoint(currentNode))
                }
            }
        }
        return trackPoints
    }

    private fun parseWayPoint(node: Node): WayPoint {
        var elevation: Double? = null
        var time: Date? = null
        var speed: Float? = null
        var magneticDeclination: Double? = null
        var geoIdHeight: Double? = null
        var name: String? = null
        var comment: String? = null
        var description: String? = null
        var src: String? = null
        var sym: String? = null
        var type: String? = null
        var fix: FixType? = null
        var sat: Int? = null
        var hDop: Double? = null
        var vDop: Double? = null
        var pDop: Double? = null
        var ageOfGPSData: Double? = null
        var dGpsId: Int? = null

        val childNodes = node.childNodes
        if (childNodes != null) {
            for (idx in 0 until childNodes.length) {
                val currentNode = childNodes.item(idx)
                when (currentNode.nodeName) {
                    ELE_NODE -> elevation = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    TIME_NODE -> time = parseGpxTime(currentNode.firstChild.nodeValue)
                    NAME_NODE -> name = currentNode.firstChild.nodeValue
                    SPEED_NODE -> speed = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toFloatOrNull()
                    CMT_NODE -> comment = currentNode.firstChild.nodeValue
                    DESC_NODE -> description = currentNode.firstChild.nodeValue
                    SRC_NODE -> src = currentNode.firstChild.nodeValue
                    MAGVAR_NODE -> magneticDeclination = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    GEOIDHEIGHT_NODE -> geoIdHeight = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    SYM_NODE -> sym = currentNode.firstChild.nodeValue
                    FIX_NODE -> fix =
                        FixType.entries.first { it.value == node.firstChild.nodeValue }
                    TYPE_NODE -> type = currentNode.firstChild.nodeValue
                    SAT_NODE -> sat = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toIntOrNull()
                    HDOP_NODE -> hDop = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    VDOP_NODE -> vDop = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    PDOP_NODE -> pDop = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    AGEOFGPSDATA_NODE -> ageOfGPSData = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toDoubleOrNull()
                    DGPSID_NODE -> dGpsId = sanitizeNodeValue(currentNode.firstChild.nodeValue)?.toIntOrNull()
                }
            }
        }

        return WayPoint(
            latitude = sanitizeNodeValue(node.attributes.getNamedItem(LAT_ATTR).nodeValue)?.toDoubleOrNull() ?: 0.0,
            longitude = sanitizeNodeValue(node.attributes.getNamedItem(LON_ATTR).nodeValue)?.toDoubleOrNull() ?: 0.0,
            elevation = elevation,
            time = time,
            speed = speed,
            magneticDeclination = magneticDeclination,
            geoIdHeight = geoIdHeight,
            name = name,
            comment = comment,
            description = description,
            src = src,
            sym = sym,
            type = type,
            fix = fix,
            sat = sat,
            hDop = hDop,
            vDop = vDop,
            pDop = pDop,
            ageOfGPSData = ageOfGPSData,
            dGpsId = dGpsId
        )
    }

    @VisibleForTesting
    fun sanitizeNodeValue(nodeValue: String?): String? {
        return nodeValue
            ?.trim()                // remove spaces/newlines
            ?.trim('"', '\'')       // strip stray quotes
            ?.replace(',', '.')     // normalize decimal separator
    }

    @VisibleForTesting
    fun parseGpxTime(raw: String): Date? {
        // GPX spec uses Z for UTC — replace with +0000
        val adjusted = if (raw.endsWith("Z")) {
            raw.dropLast(1) + "+0000"
        } else raw

        for (fmt in formats) {
            try {
                return fmt.parse(adjusted)
            } catch (_: Exception) { }
        }
        return null
    }
}
