package com.example.wimap;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.room.ForeignKey;
import androidx.room.TypeConverters;
import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Map;
@Entity(tableName= "fingerprint_table", foreignKeys = @ForeignKey(entity = MapSession.class, parentColumns = "id", childColumns = "map_owner_id", onDelete = ForeignKey.CASCADE))
public class Fingerprint {
    @PrimaryKey(autoGenerate = true)
    public int id;

    @ColumnInfo(name = "map_owner_id", index = true)
    public long mapOwnerId;


    @ColumnInfo(name = "x_coordinate")
    public final double x;

    @ColumnInfo(name = "y_coordinate")
    public final double y;

    @ColumnInfo(name = "rssi_map_json")
    @TypeConverters(RssiMapConverter.class)
    public Map<String, Double> rssiMap;

    public Fingerprint(long mapOwnerId, double x, double y, Map<String, Double> rssiMap){
        this.mapOwnerId = mapOwnerId;
        this.x = x;
        this.y = y;
        this.rssiMap = rssiMap;
    }

    public static class RssiMapConverter{
        @TypeConverter
        public static Map<String, Double> toMap(String json){
            if(json == null){
                return null;
            }
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Double>>(){}.getType();
            return gson.fromJson(json, type);

        }

        @TypeConverter
        public static String fromMap(Map<String, Double> map){
            if(map == null){
                return null;
            }
            Gson gson = new Gson();
            return gson.toJson(map);
        }
    }


}
