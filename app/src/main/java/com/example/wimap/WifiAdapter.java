package com.example.wimap;

import android.net.wifi.ScanResult;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.WifiViewHolder>{


    private final List<WifiNetwork> wifiList;

    public WifiAdapter(List<WifiNetwork> wifiList){
        this.wifiList = wifiList;
    }

    // Creating a ViewHolder by inflating the layout
    @NonNull
    @Override
    public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType){
        View itemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.wifi_list_item, parent, false);
        return new WifiViewHolder(itemView);

    }

    // Binding the data from our wifiList to the views
    @Override
    public void onBindViewHolder(@NonNull WifiViewHolder holder, int position){
        // Get data for the current position
        WifiNetwork currentNetwork = wifiList.get(position);
        // Set the text for the ssid
        holder.ssidTextView.setText(currentNetwork.ssid);
        holder.bssidTextView.setText(currentNetwork.bssid);
        // Set the strength text based on the measurement state
        if(currentNetwork.isMeasurementComplete){
            // All 3 measurements are done, show the average
            String avgText = String.format(Locale.getDefault(), "Average: %.2f dBm %s", currentNetwork.getAverageSignalStrength(),currentNetwork.signalStrengths.toString());
            holder.strengthTextView.setText(avgText);

        }else {
            // Still measuring
            String measuringText = String.format(Locale.getDefault(), "Measuring... %d/3", currentNetwork.signalStrengths.size() );
            holder.strengthTextView.setText(measuringText);
        }


    }

    @Override
    public int getItemCount(){
        return wifiList.size();
    }

    static class WifiViewHolder extends RecyclerView.ViewHolder{
        final TextView ssidTextView;
        final TextView bssidTextView;
        final TextView strengthTextView;

        public WifiViewHolder(@NonNull View itemView){
            super(itemView);
            ssidTextView = itemView.findViewById(R.id.text_wifi_ssid);
            bssidTextView = itemView.findViewById(R.id.text_wifi_bssid);
            strengthTextView = itemView.findViewById(R.id.text_wifi_strength);

        }

    }
}
