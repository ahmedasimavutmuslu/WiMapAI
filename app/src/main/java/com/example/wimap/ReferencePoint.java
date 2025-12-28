package com.example.wimap;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;


@Entity(tableName = "reference_point_table", indices = {@Index(value = {"bssid"}, unique = true)} )
public class ReferencePoint {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String bssid;
    public String ssid;
    public double x;
    public double y;

    // RSSI measured at 1 meter from this reference point
    public double measuredPowerAtOneMeter;

    public ReferencePoint(String bssid, String ssid, double x, double y, double measuredPowerAtOneMeter){
        this.bssid = bssid;
        this.ssid = ssid;
        this.x = x;
        this.y = y;
        this.measuredPowerAtOneMeter = measuredPowerAtOneMeter;
    }
}
