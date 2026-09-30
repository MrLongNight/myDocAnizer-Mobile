package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.BankStatementEntryEntity
import com.example.model.CashTransactionEntity
import com.example.model.CustomDashboardWidgetEntity
import com.example.model.CustomFieldEntity
import com.example.model.DocumentCustomFieldValueEntity
import com.example.model.DocumentEntity

@Database(
    entities = [
        DocumentEntity::class,
        CustomFieldEntity::class,
        DocumentCustomFieldValueEntity::class,
        CashTransactionEntity::class,
        BankStatementEntryEntity::class,
        CustomDashboardWidgetEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun customFieldDao(): CustomFieldDao
    abstract fun financeDao(): FinanceDao
    abstract fun customDashboardWidgetDao(): CustomDashboardWidgetDao

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

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cash_transactions` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `type` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `relatedDocumentId` INTEGER,
                        `note` TEXT NOT NULL,
                        `isMatchedWithBankStatement` INTEGER NOT NULL,
                        `matchReconciliationMonth` TEXT,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_date` ON `cash_transactions` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_transactions_type` ON `cash_transactions` (`type`)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `bank_statement_entries` (
                        `id` TEXT NOT NULL,
                        `date` INTEGER NOT NULL,
                        `bookingText` TEXT NOT NULL,
                        `purpose` TEXT NOT NULL,
                        `amount` REAL NOT NULL,
                        `isCashWithdrawal` INTEGER NOT NULL,
                        `matchedCashTransactionId` TEXT,
                        `monthYear` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_statement_entries_date` ON `bank_statement_entries` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bank_statement_entries_isCashWithdrawal` ON `bank_statement_entries` (`isCashWithdrawal`)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_dashboard_widgets` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `subtitle` TEXT NOT NULL,
                        `widgetType` TEXT NOT NULL,
                        `targetMainCategory` TEXT NOT NULL,
                        `targetSubCategory` TEXT NOT NULL,
                        `targetCustomFieldId` TEXT NOT NULL,
                        `targetCustomFieldValue` TEXT NOT NULL,
                        `numericLimit` REAL NOT NULL,
                        `noteContent` TEXT NOT NULL,
                        `colorHex` INTEGER NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `isEnabled` INTEGER NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_dashboard_widgets_position` ON `custom_dashboard_widgets` (`position`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_custom_dashboard_widgets_widgetType` ON `custom_dashboard_widgets` (`widgetType`)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `customIcon` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `documents` ADD COLUMN `companyLogo` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `custom_fields` ADD COLUMN `iconName` TEXT NOT NULL DEFAULT 'label'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mydocanizer_database"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
