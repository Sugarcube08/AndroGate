package me.capcom.smsgateway.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.RecipientState
import me.capcom.smsgateway.modules.device.db.DeviceKey
import me.capcom.smsgateway.modules.device.db.DeviceKeysDao
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry

@Database(
    entities = [
        Message::class,
        MessageRecipient::class,
        RecipientState::class,
        MessageState::class,
        LogEntry::class,
        DeviceKey::class,
    ],
    version = 26,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 8, to = 9),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
        AutoMigration(from = 11, to = 12),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 14, to = 15),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21),
        AutoMigration(from = 22, to = 23),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
    ]
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messagesDao(): MessagesDao
    abstract fun logDao(): LogEntriesDao
    abstract fun deviceKeysDao(): DeviceKeysDao

    companion object {
        fun getDatabase(context: android.content.Context): AppDatabase {
            val legacyDb = context.getDatabasePath("gateway")
            val newDb = context.getDatabasePath("androgate.db")
            if (legacyDb.exists() && !newDb.exists()) {
                legacyDb.renameTo(newDb)
                val legacyShm = context.getDatabasePath("gateway-shm")
                val legacyWal = context.getDatabasePath("gateway-wal")
                if (legacyShm.exists()) legacyShm.renameTo(context.getDatabasePath("androgate.db-shm"))
                if (legacyWal.exists()) legacyWal.renameTo(context.getDatabasePath("androgate.db-wal"))
            }

            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "androgate.db"
            )
                .addMigrations(
                    MIGRATION_7_8,
                    MIGRATION_13_14,
                    MIGRATION_21_22,
                    MIGRATION_25_26,
                )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
