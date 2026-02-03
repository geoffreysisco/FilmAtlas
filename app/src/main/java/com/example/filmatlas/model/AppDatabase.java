// AppDatabase.java (COPY/PASTE REPLACEMENT)
package com.example.filmatlas.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = { GenreCacheEntity.class, FavoriteMovieEntity.class },
        version = 3,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // Existing DAO
    public abstract GenreCacheDao genreDao();

    // NEW DAO
    public abstract FavoriteMovieDao favoriteMovieDao();

    // =====================
    // Migrations
    // =====================

    // Your existing migration (kept as-is)
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS genres");
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS genres (" +
                            "id INTEGER NOT NULL, " +
                            "name TEXT NOT NULL, " +
                            "PRIMARY KEY(id))"
            );
        }
    };

    // NEW migration: creates favorites table only (does not touch genres)
    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS favorite_movies (" +
                            "movieId INTEGER NOT NULL, " +
                            "title TEXT, " +
                            "posterPath TEXT, " +
                            "backdropPath TEXT, " +
                            "overview TEXT, " +
                            "releaseDate TEXT, " +
                            "voteAverage REAL NOT NULL, " +
                            "addedAt INTEGER NOT NULL, " +
                            "PRIMARY KEY(movieId))"
            );
        }
    };

    // =====================
    // Singleton
    // =====================

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "filmatlas.db"
                            )
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
