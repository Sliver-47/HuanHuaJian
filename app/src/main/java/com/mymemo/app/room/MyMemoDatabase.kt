package com.mymemo.app.room

import android.app.Application
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mymemo.app.room.dao.BaseNoteDao
import com.mymemo.app.room.dao.CommonDao
import com.mymemo.app.room.dao.LabelDao

@TypeConverters(Converters::class)
@Database(entities = [BaseNote::class, Label::class], version = 5)
abstract class MyMemoDatabase : RoomDatabase() {

    abstract fun getLabelDao(): LabelDao
    abstract fun getCommonDao(): CommonDao
    abstract fun getBaseNoteDao(): BaseNoteDao

    fun checkpoint() {
        getBaseNoteDao().query(SimpleSQLiteQuery("pragma wal_checkpoint(FULL)"))
    }

    companion object {

        const val DatabaseName = "MyMemoDatabase"

        @Volatile
        private var instance: MyMemoDatabase? = null

        /**
         * allowMainThreadQueries() is only used in [com.mymemo.app.ReminderReceiver.onReceive]
         * when an alarm fires off!
         */
        fun getDatabase(app: Application): MyMemoDatabase {
            return instance ?: synchronized(this) {
                val instance = Room.databaseBuilder(app, MyMemoDatabase::class.java, DatabaseName)
                    .addMigrations(Migration2, Migration3, Migration4, Migration5)
                    .allowMainThreadQueries()
                    .build()
                this.instance = instance
                return instance
            }
        }

        object Migration2 : Migration(1, 2) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `color` TEXT NOT NULL DEFAULT 'DEFAULT'")
            }
        }

        object Migration3 : Migration(2, 3) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `images` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        object Migration4 : Migration(3, 4) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `audios` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        object Migration5 : Migration(4, 5) {

            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `BaseNote` ADD COLUMN `reminder` TEXT")
            }
        }
    }
}




