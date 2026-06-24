package com.inkvault.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class Converters {
    @TypeConverter
    fun syncStateToString(s: SyncState): String = s.name

    @TypeConverter
    fun stringToSyncState(s: String): SyncState = SyncState.valueOf(s)
}

@Database(
    entities = [
        NotebookEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        PendingDotEntity::class,
        OutboxEntry::class,
        ExportRecord::class,
        RecordingEntity::class,
        PageTag::class,
        PageFts::class,
    ],
    version = 9,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class InkDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun pageDao(): PageDao
    abstract fun strokeDao(): StrokeDao
    abstract fun pendingDotDao(): PendingDotDao
    abstract fun outboxDao(): OutboxDao
    abstract fun ingestDao(): IngestDao
    abstract fun exportDao(): ExportDao
    abstract fun recordingDao(): RecordingDao
    abstract fun tagDao(): TagDao
}

/**
 * v4 → v5: add the `recordings` table WITHOUT wiping captured pages (the destructive fallback would
 * delete the user's real notes). These CREATE statements must match Room's generated schema for
 * [RecordingEntity] exactly, or the runtime identity check fails.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `recordings` (`id` TEXT NOT NULL, `pageId` TEXT NOT NULL, " +
                "`path` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_pageId` ON `recordings` (`pageId`)")
    }
}

/** v5 → v6: add the voice-note `title` column (default empty) without touching captured data. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recordings` ADD COLUMN `title` TEXT NOT NULL DEFAULT ''")
    }
}

/** v6 → v7: add the `page_tags` table (Phase E) without touching captured data. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `page_tags` (`pageId` TEXT NOT NULL, `tag` TEXT NOT NULL, " +
                "PRIMARY KEY(`pageId`, `tag`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_page_tags_tag` ON `page_tags` (`tag`)")
    }
}

/** v7 → v8: add the page `transcript` column (OCR text imported from the sync folder). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `pages` ADD COLUMN `transcript` TEXT")
    }
}

/**
 * v8 → v9: add the `page_fts` full-text index over transcripts (FTS4, porter tokenizer) and backfill
 * it from every page that already has a transcript. Purely additive — no captured data is touched.
 * The CREATE VIRTUAL TABLE must match Room's generated schema for [PageFts] (name, columns,
 * tokenizer) or Room's post-migration identity check fails; the instrumented FtsMigrationTest runs
 * this migration on a real Android image so a mismatch is caught in CI, never on a user's device.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `page_fts` USING FTS4(" +
                "`pageId` TEXT, `transcript` TEXT, tokenize=porter)",
        )
        db.execSQL(
            "INSERT INTO `page_fts` (`pageId`, `transcript`) " +
                "SELECT `id`, `transcript` FROM `pages` WHERE `transcript` IS NOT NULL",
        )
    }
}
