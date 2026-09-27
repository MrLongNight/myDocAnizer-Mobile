package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.DocumentEntity

@Database(entities = [DocumentEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mydocanizer_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
