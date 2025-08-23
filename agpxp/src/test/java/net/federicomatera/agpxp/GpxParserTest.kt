package net.federicomatera.agpxp

import org.junit.Test

import org.junit.Assert.*

/**
 * Tests the behaviour of GpxParser().
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class GpxParserTest {
    @Test
    fun test_metaData() {
        val inputStream = javaClass.classLoader!!.getResourceAsStream("meta_data.gpx")

        val gpxParser = GpxParser()
        val parsedGpx = gpxParser.parse(inputStream)
        inputStream.close()

        val metadata = parsedGpx.metadata
        assertNotNull(metadata)

        val parsedTime = metadata?.time
        val wantedTime = gpxParser.parseGpxTime("2025-08-16T15:16:32.000Z")
        assertEquals(wantedTime, parsedTime)

        val link = metadata?.link
        assertNotNull(metadata?.link)
        val parsedHref = link?.href
        val wantedHref = "connect.garmin.com"

        assertEquals(wantedHref, parsedHref)

        val parsedText = link?.text
        val wantedText = "Garmin Connect"
        assertEquals(wantedText, parsedText)

        assertEquals(825.649, gpxParser.sanitizeNodeValue("\"825.649\"")?.toDouble())
    }
}