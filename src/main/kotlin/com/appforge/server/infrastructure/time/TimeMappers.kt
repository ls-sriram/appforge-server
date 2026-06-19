package com.appforge.server.infrastructure.time

import com.appforge.server.api.ProtoTimestamp
import java.sql.PreparedStatement
import java.sql.Timestamp

fun PreparedStatement.setInstant(index: Int, value: AppTimestamp?) {
    if (value == null) {
        setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
        return
    }
    setTimestamp(index, Timestamp.from(value))
}

fun protoTimestampToInstant(value: ProtoTimestamp?): AppTimestamp? {
    if (value == null) return null
    val nanos = value.nanos.coerceIn(0, 999_999_999)
    return java.time.Instant.ofEpochSecond(value.seconds, nanos.toLong())
}

fun instantToProtoTimestamp(value: AppTimestamp?): ProtoTimestamp? {
    if (value == null) return null
    return ProtoTimestamp(
        seconds = value.epochSecond,
        nanos = value.nano,
    )
}
