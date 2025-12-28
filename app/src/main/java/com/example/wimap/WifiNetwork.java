package com.example.wimap;

import java.util.ArrayList;
import java.util.List;

/**
 * A data class to hold information about a WiFi network.
 *
 */

public class WifiNetwork {
    public final String ssid;
    public final String bssid;
    public final List<Integer> signalStrengths = new ArrayList<>();
    public boolean isMeasurementComplete = false;

    public WifiNetwork(String ssid, String bssid){
        this.ssid = ssid;
        this.bssid = bssid;
    }


    // Calculates the average signal strength;
    public double getAverageSignalStrength(){
        if(signalStrengths.isEmpty()){
            return 0;
        }
        double sum = 0;
        for(int signalStrength : signalStrengths){
            sum += signalStrength;
        }

        return sum / signalStrengths.size();
    }
}
