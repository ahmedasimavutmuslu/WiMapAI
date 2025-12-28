package com.example.wimap;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;
import android.widget.Button;



import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MapDetailActivity extends AppCompatActivity{
    public static final String EXTRA_MAP_ID = "com.example.wimap.EXTRA_MAP_ID";
    public static final String EXTRA_MAP_NAME = "com.example.wimap.EXTRA_MAP_NAME";
    public static final String EXTRA_USER_X = "com.example.wimap.EXTRA_USER_X";

    public static final String EXTRA_USER_Y = "com.example.wimap.EXTRA_USER_Y";

    private TextView textMapName;
    private HeatMapView heatmapView;

    private Button buttonGenerateHeatmap;
    private MapPointAdapter adapter;
    private final List<Fingerprint> fingerprintList = new ArrayList<>();

    private List<ReferencePoint> referencePoints = new ArrayList<>(); // To hold our anchors
    private FingerprintDao fingerprintDao;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_detail);

        textMapName = findViewById(R.id.text_map_name_detail);
        heatmapView = findViewById(R.id.heatmap_view);
        buttonGenerateHeatmap = findViewById(R.id.button_generate_heatmap);


        long mapId = getIntent().getLongExtra(EXTRA_MAP_ID,-1);
        String mapName = getIntent().getStringExtra(EXTRA_MAP_NAME);

        if(mapId == -1 || mapName == null){
            Toast.makeText(this, "Error: Invailid Map ID or data.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        textMapName.setText(mapName);
        buttonGenerateHeatmap.setEnabled(false);

        double userX = getIntent().getDoubleExtra(EXTRA_USER_X, -1);
        double userY = getIntent().getDoubleExtra(EXTRA_USER_Y, -1);


        // Getting database instance and fetching data
        FingerprintRoomDatabase db = FingerprintRoomDatabase.getDatabase(this);
        fingerprintDao = db.fingerprintDao();

        fingerprintDao.getAllReferencePoints().observe(this, refPoints -> {
            referencePoints.clear();
            referencePoints.addAll(refPoints);
            tryGenerateMap(userX, userY);
        });

        // Observing the fingerprints for the specific map ID passed in the intent
        fingerprintDao.getFingerprintsForMap(mapId).observe(this,fingerprints -> {
            fingerprintList.clear();
            fingerprintList.addAll(fingerprints);
            tryGenerateMap(userX, userY);

        });

        buttonGenerateHeatmap.setOnClickListener(v -> {
            Toast.makeText(this, "Generating composite heatmap...", Toast.LENGTH_SHORT).show();
            tryGenerateMap(-1, -1);
        });
    }

    /**
     * Helper method that checks if all required data is loaded before generating the heatmap.
     */
    private void tryGenerateMap(double userX, double userY) {
        // Only proceed if BOTH the anchors AND the fingerprint data have been loaded.
        if (!referencePoints.isEmpty() && !fingerprintList.isEmpty()) {
            buttonGenerateHeatmap.setEnabled(true);
            heatmapView.post(() -> {
                List<String> anchorBssids = getAnchorBssids();
                heatmapView.generateHeatmap(fingerprintList, anchorBssids, heatmapView.getWidth(), heatmapView.getHeight(), userX, userY);
            });
        }
    }

    /**
     * Helper method to extract BSSIDs from the loaded reference points.
     */
    private List<String> getAnchorBssids() {
        List<String> bssids = new ArrayList<>();
        for (ReferencePoint rp : referencePoints) {
            bssids.add(rp.bssid);
        }
        return bssids;
    }

    /**
     * Generates a simple text-based grid representation of the heatmap data
     * and prints it to the Android Logcat
     */
    private void generateTextHeatmap(List<Fingerprint> points, String targetSsid){
        if(points.isEmpty()){
            return;
        }

        // Find the boundaries of the map
        double minX = points.get(0).x, maxX = points.get(0).x;
        double minY = points.get(0).y, maxY = points.get(0).y;
        for(Fingerprint point : points){
            if(point.x < minX){
                minX = point.x;
            }

            if(point.x > maxX){
                maxX = point.x;
            }

            if(point.y < minY){
                minY = point.y;
            }

            if(point.y > maxY){
                maxY = point.y;
            }
        }

        Log.d("Heatmap", "--- Generating Heatmap for " + targetSsid + " ---" );
        // Loop from max Y down to min Y to print the grid top-to-bottom
        for(double y = maxY; y>= minY; y--){
            StringBuilder row = new StringBuilder();
            row.append(String.format(Locale.getDefault(),"Y=%.1f | ",y));
            for(double x = minX; x <= maxX; x++){
                boolean pointFound = false;
                for(Fingerprint point : points){
                    if(point.x == x && point.y == y){
                        double rssi = point.rssiMap.getOrDefault(targetSsid, -100.0);
                        row.append(String.format(Locale.getDefault(),"[%4.0f} ",rssi));
                        pointFound = true;
                        break;
                    }
                }
                if(!pointFound){
                    row.append("[----] "); // Empty grid cell
                }

            }
            Log.d("Heatmap",row.toString());

        }
        Log.d("Heatmap","------------------------------------------");


    }

}
