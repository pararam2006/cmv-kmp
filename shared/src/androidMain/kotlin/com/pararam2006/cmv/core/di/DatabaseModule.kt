package com.pararam2006.cmv.core.di

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pararam2006.cmv.data.local.AppDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE track_volumes ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create new table
        db.execSQL(
            """
            CREATE TABLE track_volumes_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackTitle TEXT NOT NULL,
                artistName TEXT,
                volumeOffset REAL NOT NULL,
                isDeleted INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent()
        )

        // Copy the data. Convert Old INT to Float (rough fraction assuming max volume was 15)
        db.execSQL(
            """
            INSERT INTO track_volumes_new (id, trackTitle, artistName, volumeOffset, isDeleted)
            SELECT id, trackTitle, artistName, CAST(volumeOffset AS REAL) / 15.0, isDeleted FROM track_volumes
        """.trimIndent()
        )

        // Drop the old table
        db.execSQL("DROP TABLE track_volumes")

        // Rename the new table
        db.execSQL("ALTER TABLE track_volumes_new RENAME TO track_volumes")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
                CREATE TABLE apps (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    label TEXT NOT NULL,
                    iconUri TEXT NOT NULL, 
                    packageName TEXT NOT NULL,
                    name TEXT NOT NULL,
                    selected INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM track_volumes WHERE isDeleted = 1")
        db.execSQL(
            """
            CREATE TABLE track_volumes_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackTitle TEXT NOT NULL,
                artistName TEXT,
                volumeOffset REAL NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO track_volumes_new (id, trackTitle, artistName, volumeOffset)
            SELECT id, trackTitle, artistName, volumeOffset FROM track_volumes
            """.trimIndent()
        )
        db.execSQL("DROP TABLE track_volumes")
        db.execSQL("ALTER TABLE track_volumes_new RENAME TO track_volumes")
    }
}

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
        )
            .build()
    }
    single { get<AppDatabase>().trackVolumeDao() }
    single { get<AppDatabase>().appsDao() }
}
