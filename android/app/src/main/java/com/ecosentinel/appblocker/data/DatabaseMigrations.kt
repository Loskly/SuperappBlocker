package com.ecosentinel.appblocker.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `focus_session` (
                    `id` INTEGER NOT NULL,
                    `active` INTEGER NOT NULL,
                    `mode` TEXT NOT NULL,
                    `startedAtMillis` INTEGER NOT NULL,
                    `expiresAtMillis` INTEGER NOT NULL,
                    `blockedCategoryIdsJson` TEXT NOT NULL,
                    `blockedPackagesJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cooldown_state` (
                    `ruleId` TEXT NOT NULL,
                    `blockedUntilMillis` INTEGER NOT NULL,
                    `triggeredAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`ruleId`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            migrateLegacyRuleIds(db, "app:")
            migrateLegacyRuleIds(db, "category:")
        }

        private fun migrateLegacyRuleIds(db: SupportSQLiteDatabase, prefix: String) {
            db.query(
                """
                SELECT id, blockMode FROM policy_rules
                WHERE id LIKE ? AND id NOT LIKE ?
                """.trimIndent(),
                arrayOf("$prefix%", "$prefix%:%")
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val oldId = cursor.getString(0)
                    val blockMode = cursor.getString(1)
                    val newId = "$oldId:$blockMode"
                    db.execSQL(
                        "UPDATE policy_rules SET id = ? WHERE id = ?",
                        arrayOf(newId, oldId)
                    )
                    db.execSQL(
                        "UPDATE cooldown_state SET ruleId = ? WHERE ruleId = ?",
                        arrayOf(newId, oldId)
                    )
                }
            }
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `super_alarms` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `hour` INTEGER NOT NULL,
                    `minute` INTEGER NOT NULL,
                    `repeatDaysMask` INTEGER NOT NULL,
                    `label` TEXT NOT NULL,
                    `enabled` INTEGER NOT NULL,
                    `challengeDifficulty` TEXT NOT NULL,
                    `volumePercent` INTEGER NOT NULL,
                    `volumeGuardEnabled` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_groups` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `app_group_members` (
                    `groupId` TEXT NOT NULL,
                    `packageName` TEXT NOT NULL,
                    PRIMARY KEY(`groupId`, `packageName`)
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `super_alarms` ADD COLUMN `soundUri` TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `todo_items` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `note` TEXT NOT NULL,
                    `dueAtMillis` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `createdAtMillis` INTEGER NOT NULL,
                    `completedAtMillis` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `super_alarms` ADD COLUMN `soundDisplayName` TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `food_entries` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `timestamp` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `calories` INTEGER NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `food_entries` ADD COLUMN `photoPath` TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockMode` TEXT NOT NULL DEFAULT 'NORMAL'"
            )
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockUntilDayEndMillis` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockUntilCustomMillis` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockOnBlockActive` INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockDelayMinutes` INTEGER"
            )
            db.execSQL(
                "ALTER TABLE `policy_rules` ADD COLUMN `lockDelayStartedAtMillis` INTEGER"
            )
        }
    }

    val ALL = arrayOf(
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14
    )
}
