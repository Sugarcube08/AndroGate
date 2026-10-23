package me.capcom.smsgateway.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            UPDATE message
            SET validUntil = strftime('%FT%TZ', createdAt / 1000 + 86400, 'unixepoch')
            WHERE validUntil IS NULL AND state = 'Pending'
        """.trimIndent()
        )
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Create a new table with the desired schema (without the text column)
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS message_new (
                `id` TEXT NOT NULL,
                `withDeliveryReport` INTEGER NOT NULL DEFAULT 1,
                `simNumber` INTEGER,
                `validUntil` TEXT,
                `isEncrypted` INTEGER NOT NULL DEFAULT 0,
                `skipPhoneValidation` INTEGER NOT NULL DEFAULT 0,
                `priority` INTEGER NOT NULL DEFAULT 0,
                `type` TEXT NOT NULL DEFAULT 'Text',
                `source` TEXT NOT NULL DEFAULT 'Local',
                `content` TEXT NOT NULL,
                `state` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL DEFAULT 0,
                `processedAt` INTEGER,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )

        // Copy data from the old table to the new table
        database.execSQL(
            """
            INSERT INTO message_new (
                id,
                withDeliveryReport,
                simNumber,
                validUntil,
                isEncrypted,
                skipPhoneValidation,
                priority,
                type,
                source,
                content,
                state,
                createdAt,
                processedAt
            )
            SELECT
                id,
                withDeliveryReport,
                simNumber,
                validUntil,
                isEncrypted,
                skipPhoneValidation,
                priority,
                'Text',
                'Local',
                '{"text":"' || replace(replace(text, '\', '\\'), '"', '\"') || '"}',
                state,
                createdAt,
                processedAt
            FROM message
        """.trimIndent()
        )

        // Drop the old table and rename the new table to the original name
        database.execSQL("DROP TABLE IF EXISTS message")
        database.execSQL("ALTER TABLE message_new RENAME TO message")

        // Create indices
        database.execSQL("CREATE INDEX IF NOT EXISTS index_Message_state ON message (state)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_Message_createdAt ON message (createdAt)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_Message_processedAt ON message (processedAt)")
    }
}

val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            DELETE FROM incoming_messages WHERE 1
        """.trimIndent()
        )
    }
}

val MIGRATION_25_26 = object : Migration(25, 26) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("DROP TABLE IF EXISTS webhooks")
        database.execSQL("DROP TABLE IF EXISTS webhook_queue")
        database.execSQL("DROP TABLE IF EXISTS webhook_queue_entries")
        database.execSQL("DROP TABLE IF EXISTS inbox_uploads")
        database.execSQL("DROP TABLE IF EXISTS tokens")
        database.execSQL("DROP TABLE IF EXISTS incoming_messages")
    }
}
