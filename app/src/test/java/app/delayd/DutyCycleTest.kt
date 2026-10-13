package app.delayd

import android.location.LocationManager
import org.junit.Assert.assertEquals
import org.junit.Test

class DutyCycleTest {
    @Test
    fun `riding uses GPS every 10 seconds`() = assertEquals(DutyCycle.Plan(true, 10_000), DutyCycle.plan(true, false, true))

    @Test
    fun `after movement the sensor reports we look closer for a while`() =
        assertEquals(DutyCycle.Plan(false, 15_000), DutyCycle.plan(false, true, true))

    @Test
    fun `idle polls slowly only when a motion sensor wakes us`() {
        assertEquals(300_000L, DutyCycle.plan(false, false, true).intervalMs)
        assertEquals(60_000L, DutyCycle.plan(false, false, false).intervalMs)
    }

    @Test
    fun `falls back to the provider that is on`() {
        assertEquals(LocationManager.NETWORK_PROVIDER, DutyCycle.provider(true, gpsOn = false, networkOn = true))
        assertEquals(LocationManager.GPS_PROVIDER, DutyCycle.provider(false, gpsOn = true, networkOn = false))
        assertEquals(LocationManager.GPS_PROVIDER, DutyCycle.provider(true, gpsOn = true, networkOn = true))
        assertEquals(LocationManager.NETWORK_PROVIDER, DutyCycle.provider(false, gpsOn = true, networkOn = true))
    }

    @Test
    fun `with everything off GPS waits to be enabled`() =
        assertEquals(LocationManager.GPS_PROVIDER, DutyCycle.provider(false, gpsOn = false, networkOn = false))
}
