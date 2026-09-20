package io.github.command1264.invitecode.testkit

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FixedClock(private val instant: Instant, private val zone: ZoneId = ZoneId.of("UTC")) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = FixedClock(instant, zone)

    override fun instant(): Instant = instant
}
