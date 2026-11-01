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
import com.kairos.app.data.model.TimeBlock

@Database(
    entities = [Task::class, Label::class, Subtask::class, SavedFilter::class, Attachment::class, TimeBlock::class],
    version = 6,
    exportSchema = false,
)
abstract class KairosDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun labelDao(): LabelDao
    abstract fun subtaskDao(): SubtaskDao
    abstract fun savedFilterDao(): SavedFilterDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun timeBlockDao(): TimeBlockDao

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

        /**
         * v4 → v5 : ordre manuel des étiquettes. Les positions initiales reprennent l'ordre de création
         * (rang de chaque étiquette), pour que rien ne bouge visuellement après la mise à jour.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `labels` ADD COLUMN `position` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "UPDATE `labels` SET `position` = (SELECT COUNT(*) FROM `labels` AS l2 " +
                        "WHERE l2.`createdAt` < `labels`.`createdAt` " +
                        "OR (l2.`createdAt` = `labels`.`createdAt` AND l2.`id` < `labels`.`id`))",
                )
            }
        }

        /**
         * v5 → v6 : plusieurs sessions par tâche et durée estimée. Les créneaux existants deviennent
         * des lignes de `time_blocks` ; une tâche déjà terminée garde le sien comme session terminée.
         * `tasks` est recréée (DROP COLUMN n'existe pas sous l'API 34). Room n'active les clés
         * étrangères qu'après les migrations : le DROP ne supprime donc pas les sous-tâches.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `time_blocks` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`taskId` INTEGER NOT NULL, " +
                        "`start` INTEGER NOT NULL, " +
                        "`minutes` INTEGER NOT NULL, " +
                        "`doneAt` INTEGER, " +
                        "FOREIGN KEY(`taskId`) REFERENCES `tasks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_blocks_taskId` ON `time_blocks` (`taskId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_time_blocks_start` ON `time_blocks` (`start`)")
                db.execSQL(
                    "INSERT INTO `time_blocks` (`taskId`, `start`, `minutes`, `doneAt`) " +
                        "SELECT `id`, `blockStart`, " +
                        "CASE WHEN `blockMinutes` > 0 THEN `blockMinutes` ELSE ${Task.DEFAULT_BLOCK_MINUTES} END, " +
                        "`completedAt` FROM `tasks` WHERE `blockStart` IS NOT NULL",
                )
                db.execSQL(
                    "CREATE TABLE `tasks_new` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`important` INTEGER NOT NULL, " +
                        "`urgent` INTEGER NOT NULL, " +
                        "`dueAt` INTEGER, " +
                        "`labelId` INTEGER, " +
                        "`position` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL, " +
                        "`completedAt` INTEGER, " +
                        "`removedAt` INTEGER, " +
                        "`recurrence` INTEGER NOT NULL DEFAULT 0, " +
                        "`focusSeconds` INTEGER NOT NULL DEFAULT 0, " +
                        "`estimateMinutes` INTEGER NOT NULL DEFAULT 0)",
                )
                db.execSQL(
                    "INSERT INTO `tasks_new` (`id`, `title`, `note`, `important`, `urgent`, `dueAt`, `labelId`, " +
                        "`position`, `createdAt`, `completedAt`, `removedAt`, `recurrence`, `focusSeconds`) " +
                        "SELECT `id`, `title`, `note`, `important`, `urgent`, `dueAt`, `labelId`, " +
                        "`position`, `createdAt`, `completedAt`, `removedAt`, `recurrence`, `focusSeconds` FROM `tasks`",
                )
                db.execSQL("DROP TABLE `tasks`")
                db.execSQL("ALTER TABLE `tasks_new` RENAME TO `tasks`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_labelId` ON `tasks` (`labelId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_dueAt` ON `tasks` (`dueAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tasks_completedAt` ON `tasks` (`completedAt`)")
            }
        }

        fun getInstance(context: Context): KairosDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KairosDatabase::class.java,
                    "kairos.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
