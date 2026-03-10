package com.swipeplayer.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeFormatTest {

    @Test fun `zero milliseconds`()            { assertEquals("00:00",      0L.toTimecode()) }
    @Test fun `3 minutes 3 seconds`()          { assertEquals("03:03",  183_000L.toTimecode()) }
    @Test fun `1 hour 2 minutes 3 seconds`()   { assertEquals("01:02:03", 3_723_000L.toTimecode()) }
    @Test fun `4 hours exact`()                { assertEquals("04:00:00", 14_400_000L.toTimecode()) }
    @Test fun `59 minutes 59 seconds`()        { assertEquals("59:59",  3_599_000L.toTimecode()) }
    @Test fun `exactly 1 hour`()               { assertEquals("01:00:00", 3_600_000L.toTimecode()) }
    @Test fun `negative clamped to zero`()     { assertEquals("00:00",    (-5_000L).toTimecode()) }
    @Test fun `4 hours 30 minutes 15 seconds`(){ assertEquals("04:30:15", 16_215_000L.toTimecode()) }
    @Test fun `single digit minutes and seconds`(){ assertEquals("01:05", 65_000L.toTimecode()) }
}
