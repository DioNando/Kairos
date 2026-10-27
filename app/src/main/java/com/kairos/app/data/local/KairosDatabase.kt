package com.kairos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kairos.app.data.model.Attachment
import com.kairos.app.data.model.Label
import com.kairos.app.data.model.SavedFilter
import com.kairos.app.data.model.Subtask
import com.kairos.app.data.model.Task

@Database(
    entities = [Task::class, Label::class, Subtask::class, SavedFilter::class, Attachment::class],
    version = 4,
    exportSchema = false,
)
abstract class KairosDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun labelDao(): LabelDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun savedFilterDao(): SavedFilterDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        @Volatile
        private var INSTANCE: KairosDatabase? = null

        /**
         * v1 → v2 : ajoute la récurrence et le temps de concentration aux tâches, et crée la table
         * des sous-tâches. Les `DEFAULT 0` correspondent aux `@ColumnInfo(defaultValue = "0")` des
         * entités (indispensable pour que la validation de schéma Room passe après migration).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `recurrence` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `focusSeconds` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subtasks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL DEFAULT 0, " +
                        "`position` INTEGER NOT NULL DEFAULT 0, " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtasks_taskId` ON `subtasks` (`taskId`)")
            }
        }

        /**
         * v2 → v3 : ajoute `removedAt` (colonne nullable) pour distinguer les tâches **retirées**
         * (glissées hors du board) des tâches **terminées** (`completedAt`). Une colonne nullable
         * n'a pas besoin de DEFAULT : elle vaut NULL pour les lignes existantes (= non retirées).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `removedAt` INTEGER")
            }
        }

        /**
         * v3 → v4 : time-blocking (créneau planifié des tâches), échéance propre des sous-tâches,
         * groupes + épinglage des étiquettes, filtres enregistrés et pièces jointes.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `blockStart` INTEGER")
                db.execSQL("ALTER TABLE `tasks` ADD COLUMN `blockMinutes` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `subtasks` ADD COLUMN `dueAt` INTEGER")
                db.execSQL("ALTER TABLE `labels` ADD COLUMN `groupName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `labels` ADD COLUMN `pinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `saved_filters` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`query` TEXT NOT NULL, " +
                        "`labelId` INTEGER, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_taskId` ON `attachments` (`taskId`)")
            }
        }

        fun getInstance(context: Context): KairosDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KairosDatabase::class.java,
                    "kairos.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
    }
}
