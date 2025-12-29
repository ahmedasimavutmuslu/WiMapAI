/**
 * Simple class to set up the Room database for the app.
 */

package com.example.wimap;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {Fingerprint.class, MapSession.class, ReferencePoint.class}, version = 4, exportSchema = false)
@TypeConverters(Fingerprint.RssiMapConverter.class)
public abstract class FingerprintRoomDatabase extends RoomDatabase {
    public abstract FingerprintDao fingerprintDao();

    private static volatile FingerprintRoomDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
    static FingerprintRoomDatabase getDatabase(final Context context) {
        if(INSTANCE == null){
            synchronized (FingerprintRoomDatabase.class){
                if(INSTANCE == null){
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(), FingerprintRoomDatabase.class, "fingerprint_database").fallbackToDestructiveMigration().build();

                }
            }
        }
        return INSTANCE;
    }


}
