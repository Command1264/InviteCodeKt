package io.github.command1264.invitecode.storage.sql

import java.sql.Connection
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

internal fun Connection.isMysql(): Boolean = metaData.databaseProductName.equals("MySQL", ignoreCase = true)

private val MYSQL_INSTANT_FORMAT: DateTimeFormatter = DateTimeFormatterBuilder().appendInstant(9).toFormatter()

/** Fixed-width UTC text keeps lexical index ordering equal to chronological ordering in MySQL. */
internal fun Instant.toSqlText(connection: Connection): String =
    if (connection.isMysql()) MYSQL_INSTANT_FORMAT.format(this) else toString()
