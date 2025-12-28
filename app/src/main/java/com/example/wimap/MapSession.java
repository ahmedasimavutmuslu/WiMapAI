package com.example.wimap;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;


@Entity(tableName = "map_session_table")
public class MapSession {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public String mapName;

    public MapSession(String mapName){
        this.mapName = mapName;
    }

    @Override
    @NonNull
    public String toString(){
        return mapName;
    }
}
