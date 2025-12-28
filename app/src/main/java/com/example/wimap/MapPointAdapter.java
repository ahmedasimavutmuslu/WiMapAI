package com.example.wimap;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class MapPointAdapter extends RecyclerView.Adapter<MapPointAdapter.PointViewHolder> {
    private final List<Fingerprint> pointList;

    private final String targetSsid;

    public MapPointAdapter(List<Fingerprint> pointList, String targetSsid) {
        this.pointList = pointList;
        this.targetSsid = targetSsid;
    }

    @NonNull
    @Override
    public PointViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Reusing the same wifi_list_item for simplicity
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wifi_list_item, parent, false);
        return new PointViewHolder(view);

    }

    @Override
    public void onBindViewHolder(@NonNull PointViewHolder holder, int position) {
        Fingerprint currentPoint = pointList.get(position);

        // Showing the coordinates in the main text view
        String coordinateText = String.format(Locale.getDefault(), "Point (X: %.1f, Y: %.1f)",currentPoint.x, currentPoint.y);
        holder.ssidTextView.setText(coordinateText);

        // Showing the specific RSSI for the target SSID in the strength text view
        Double rssi = currentPoint.rssiMap.getOrDefault(targetSsid,-100.0);
        String rssiText = String.format(Locale.getDefault(), "RSSI for %s: %.2f dBm", targetSsid, rssi);
        holder.strengthTextView.setText(rssiText);

    }

    @Override
    public int getItemCount(){
        return pointList.size();
    }

    static class PointViewHolder extends RecyclerView.ViewHolder{
        final TextView ssidTextView;
        final TextView strengthTextView;

        public PointViewHolder(@NonNull View itemView){
            super(itemView);
            ssidTextView = itemView.findViewById(R.id.text_wifi_ssid);
            strengthTextView = itemView.findViewById(R.id.text_wifi_strength);
        }
    }
}
