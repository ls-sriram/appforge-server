package com.appforge.server.providers.time

import com.appforge.server.infrastructure.time.AppTimestamp

interface TimestampProvider {
    fun now(): AppTimestamp
}

object UtcTimestampProvider : TimestampProvider {
    override fun now(): AppTimestamp = java.time.Instant.now()
}
