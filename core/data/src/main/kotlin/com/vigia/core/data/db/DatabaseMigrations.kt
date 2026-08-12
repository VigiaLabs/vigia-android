package com.vigia.core.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Durable schema history for the on-device database.
 *
 * Version 2 added the local harsh-event outbox. Keeping this migration explicit
 * prevents an app upgrade from silently deleting chat history or trip data.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `harsh_events` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `tripId` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `magnitude` REAL NOT NULL,
                `lat` REAL NOT NULL,
                `lon` REAL NOT NULL,
                `timestampMs` INTEGER NOT NULL,
                `uploadedAt` INTEGER
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_harsh_events_tripId` ON `harsh_events` (`tripId`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_harsh_events_timestampMs` ON `harsh_events` (`timestampMs`)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `alert_inbox` (
                `id` TEXT NOT NULL,
                `severity` TEXT NOT NULL,
                `messageText` TEXT NOT NULL,
                `timestampMs` INTEGER NOT NULL,
                `latitudeDeg` REAL,
                `longitudeDeg` REAL,
                `accuracyMeters` REAL,
                `bearingDeg` REAL,
                `velocityMs` REAL,
                `locationTimestampMs` INTEGER,
                `receivedAtMs` INTEGER NOT NULL,
                `acknowledged` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_alert_inbox_timestampMs` ON `alert_inbox` (`timestampMs`)"
        )
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_alert_inbox_acknowledged` ON `alert_inbox` (`acknowledged`)"
        )
    }
}
