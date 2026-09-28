package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.CustomFieldEntity
import com.example.model.DocumentCustomFieldValueEntity
import com.example.model.DocumentEntity

@Database(
    entities = [
        DocumentEntity::class,
        CustomFieldEntity::class,
        DocumentCustomFieldValueEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun customFieldDao(): CustomFieldDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_isDeleted_createdAt` ON `documents` (`isDeleted`, `createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_docType` ON `documents` (`docType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_mainCategory` ON `documents` (`mainCategory`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_cancellationDeadline` ON `documents` (`cancellationDeadline`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_fields` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `options` TEXT NOT NULL,
                        `scope` TEXT NOT NULL,
                        `targetMainCategory` TEXT NOT NULL,
                        `targetSubCategory` TEXT NOT NULL,
                        `defaultValue` TEXT NOT NULL,
                        `isRequired` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_fields_scope` ON `custom_fields` (`scope`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_fields_targetMainCategory` ON `custom_fields` (`targetMainCategory`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `document_custom_field_values` (
                        `documentId` INTEGER NOT NULL,
                        `customFieldId` TEXT NOT NULL,
                        `fieldValue` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`documentId`, `customFieldId`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_custom_field_values_documentId` ON `document_custom_field_values` (`documentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_document_custom_field_values_customFieldId` ON `document_custom_field_values` (`customFieldId`)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mydocanizer_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
