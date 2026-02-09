package com.example.filmatlas.model;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

@Database(
        entities = {
                GenreCacheEntity.class,
                FavoriteMovieEntity.class,
                SearchQueryEntity.class
        },
        version = 4,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    // Existing DAOs
    public abstract GenreCacheDao genreDao();
    public abstract FavoriteMovieDao favoriteMovieDao();

    // NEW DAO
    public abstract SearchHistoryDao searchHistoryDao();

    // =====================
    // Migrations
    // =====================

    // Existing migration
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

    // Existing migration
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

    // NEW migration: creates search_history table
    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase db) {
            db.execSQL(
                    "CREATE TABLE IF NOT EXISTS search_history (" +
                            "query TEXT NOT NULL, " +
                            "lastUsedEpoch INTEGER NOT NULL, " +
                            "PRIMARY KEY(query))"
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
                            .addMigrations(
                                    MIGRATION_1_2,
                                    MIGRATION_2_3,
                                    MIGRATION_3_4
                            )
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}