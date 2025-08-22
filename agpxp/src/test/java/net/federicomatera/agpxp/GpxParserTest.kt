package net.federicomatera.agpxp

import org.junit.Test

import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Tests the behaviour of GpxParser().
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class GpxParserTest {
    @Test
    fun test_metaData() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss", Locale.US)
        val inputStream = javaClass.classLoader!!.getResourceAsStream("meta_data.gpx")

        val gpxParser = GpxParser()
        val parsedGpx = gpxParser.parse(inputStream)
        inputStream.close()

        val metadata = parsedGpx.metadata
        assertNotNull(metadata)

        val parsedTime = metadata?.time
        val wantedTime = dateFormat.parse("2025-08-16T15:16:32.000Z")
        assertEquals(wantedTime, parsedTime)

        val link = metadata?.link
        assertNotNull(metadata?.link)
        val parsedHref = link?.href
        val wantedHref = "connect.garmin.com"

        assertEquals(wantedHref, parsedHref)

        val parsedText = link?.text
        val wantedText = "Garmin Connect"
        assertEquals(wantedText, parsedText)
    }
}