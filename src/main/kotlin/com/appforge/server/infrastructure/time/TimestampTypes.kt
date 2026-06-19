package com.appforge.server.infrastructure.time

import java.sql.ResultSet
import java.time.Clock
import java.time.Instant

typealias AppTimestamp = Instant

fun Clock.nowTimestamp(): AppTimestamp = Instant.now(this)

fun timestampFromEpochMilli(value: Long): AppTimestamp = Instant.ofEpochMilli(value)

fun parseTimestamp(value: String): AppTimestamp = Instant.parse(value)

fun ResultSet.getAppTimestamp(column: String): AppTimestamp? =
    getTimestamp(column)?.toInstant()
