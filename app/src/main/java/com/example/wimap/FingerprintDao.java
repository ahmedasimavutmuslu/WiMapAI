package com.example.wimap;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface FingerprintDao {
    // Inserting new fingerprint. If it already exists, replacing it.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Fingerprint fingerprint);

    // Get all fingerprints from the table, ordered by their ids.
    @Query("SELECT * FROM fingerprint_table WHERE map_owner_id = :mapId")
    LiveData<List<Fingerprint>> getFingerprintsForMap(long mapId);

    // Delete all fingerprints from table
    @Query("DELETE FROM fingerprint_table")
    void deleteAll();

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertMap(MapSession mapSession); // Return the new map's id

    @Query("SELECT * FROM map_session_table ORDER BY mapName ASC")
    LiveData<List<MapSession>> getAllMaps();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertReferencePoint(ReferencePoint referencePoint);

    @Query("SELECT * FROM reference_point_table")
    LiveData<List<ReferencePoint>> getAllReferencePoints();

    @Query("DELETE FROM reference_point_table")
    void deleteAllReferencePoints();
}
