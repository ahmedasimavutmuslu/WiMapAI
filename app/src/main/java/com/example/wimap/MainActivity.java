package com.example.wimap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.*;
import android.net.wifi.*;
import android.Manifest;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity{
    WifiManager wifiManager;

    RecyclerView recyclerView;

    EditText editCoordinateX;

    EditText editCoordinateY;

    EditText editNewMapName;

    TextView textCooldownTimer;

    TextView textUserLocation;

    Button buttonScan;

    Button buttonAddFingerprint;

    Button buttonExportData;

    Button buttonCreateMap;

    Button buttonViewMap;

    List<Fingerprint> activeFingerprints = new ArrayList<>(); // Map of known locations

    Spinner spinnerNetworkSelection;

    Spinner spinnerActiveMap;

    ArrayAdapter<String> spinnerNetworkSelectionAdapter;

    ArrayAdapter<MapSession> mapSpinnerAdapter;

    List<String> networkSpinnerData = new ArrayList<>();

    Map<String, String> bssidToSsidMap = new HashMap<>();

    List<MapSession> mapSessionList = new ArrayList<>();

    MapSession activeMap; // Currently selected map

    private CountDownTimer cooldownTimer;
    private WifiAdapter wifiAdapter;
    //Custom list of WifiNetwork objects
    private final List<WifiNetwork> wifiList = new ArrayList<>();
    // Map to find networks by their SSID easily
    private final Map<String,WifiNetwork> networkMap = new HashMap<>();

    // State variables for managing the scanning process
    private boolean isScanningForMeasurements = false;
    private int scanCount = 0;
    private static final int TOTAL_SCANS = 3;

    private final Handler scanHandler = new Handler(Looper.getMainLooper());

    private FingerprintRoomDatabase db;

    private FingerprintDao fingerprintDao;

    private String currentUserRole;

    private LinearLayout adminPanel;

    private Spinner spinnerReferenceApSelection;
    private EditText editRefX;
    private EditText editRefY;
    private EditText editRefRssi1m;
    private Button buttonSaveReferencePoint;
    private List<ReferencePoint> referencePoints = new ArrayList<>();


    // This method runs after the settings panel is closed
    private final ActivityResultLauncher<Intent> settingsPanelLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result ->{
                if (wifiManager.isWifiEnabled()) {
                    // Wi-Fi is now on, we can proceed
                    // We call scanNowThatWifiIsEnabled helper method
                    Toast.makeText(this, "Wi-Fi has been enabled. Starting scan...", Toast.LENGTH_SHORT);
                    scanNowThatWifiIsEnabled();

                }else{
                    Toast.makeText(this, "Wi-Fi is still disabled. Scan is cancelled.", Toast.LENGTH_SHORT).show();
                }
            }

    );


    // Constant to identify the permission request
    private static final int LOCATION_PERMISSION_REQUEST = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Getting the user role and configuring UI
        currentUserRole = getIntent().getStringExtra("USER_ROLE");
        if(currentUserRole == null){
            // Failsafe in case the role isnt passed correctly
            currentUserRole = "USER";
        }

        adminPanel = findViewById(R.id.admin_panel);

        // Checking role and setting the visibility
        if(currentUserRole != null && currentUserRole.equals("ADMIN")){
            adminPanel.setVisibility(View.VISIBLE);
        }
        else{
            adminPanel.setVisibility(View.GONE);
        }

        // Database setup
        db = FingerprintRoomDatabase.getDatabase(this);
        fingerprintDao = db.fingerprintDao();

        buttonScan = findViewById(R.id.button_scan_wifi);
        textCooldownTimer = findViewById(R.id.text_cooldown_timer);
        textUserLocation = findViewById(R.id.text_user_location);
        recyclerView = findViewById(R.id.list_wifi_networks);
        wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        editCoordinateX = findViewById(R.id.edit_coordinate_x);
        editCoordinateY = findViewById(R.id.edit_coordinate_y);
        buttonAddFingerprint = findViewById(R.id.button_add_fingerprint);
        buttonExportData = findViewById(R.id.button_export_data);
        spinnerNetworkSelection = findViewById(R.id.spinner_network_selection);

        editNewMapName = findViewById(R.id.edit_new_map_name);
        buttonCreateMap = findViewById(R.id.button_create_map);
        buttonViewMap = findViewById(R.id.button_view_map);
        spinnerActiveMap = findViewById(R.id.spinner_active_map);

        // Recyclerview and adapter setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        wifiAdapter = new WifiAdapter(wifiList);
        recyclerView.setAdapter(wifiAdapter);

        // Spinner adapter setup for MAIN network selection
        spinnerNetworkSelectionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, networkSpinnerData);
        spinnerNetworkSelectionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNetworkSelection.setAdapter(spinnerNetworkSelectionAdapter);

        // Spinner adapter setup for map
        mapSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, mapSessionList);
        mapSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerActiveMap.setAdapter(mapSpinnerAdapter);

        // UI Elements for Admin
        spinnerReferenceApSelection = findViewById(R.id.spinner_reference_ap_selection);
        editRefX = findViewById(R.id.edit_ref_x);
        editRefY = findViewById(R.id.edit_ref_y);
        editRefRssi1m = findViewById(R.id.edit_ref_rssi_1m);
        buttonSaveReferencePoint = findViewById(R.id.button_save_reference_point);


        // Reference point spinner
        spinnerReferenceApSelection.setAdapter(spinnerNetworkSelectionAdapter);


        buttonSaveReferencePoint.setOnClickListener(v -> saveReferencePoint());

        fingerprintDao.getAllReferencePoints().observe(this, refPoints -> {
            referencePoints.clear();
            referencePoints.addAll(refPoints);

            if(referencePoints.size() >= 3){
                Toast.makeText(this, referencePoints.size() + " anchors loaded. Ready for positioning", Toast.LENGTH_SHORT).show();
            }
        });




        // Observing the changes to the list of maps
        fingerprintDao.getAllMaps().observe(this,fingerprints -> {
            // Update the in-memory list
            mapSessionList.clear();
            mapSessionList.addAll(fingerprints);
            mapSpinnerAdapter.notifyDataSetChanged();

            if(!fingerprints.isEmpty() && activeMap == null){
                spinnerActiveMap.setSelection(0);
            }
        });

        // Ask for fine location permission
        buttonScan.setOnClickListener(v -> {
            askForLocationPermission();
        });

        // Listening to see when user selects a different map
        spinnerActiveMap.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // A new map has been selected, update our activeMap
                activeMap = (MapSession) parent.getItemAtPosition(position);
                // Now observe the fingerprints only for this specific map
                observeFingerprintsForMap();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                activeMap = null;
                activeFingerprints.clear();

            }
        });

        buttonAddFingerprint.setOnClickListener(v -> saveCurrentFingerprint());
        buttonExportData.setOnClickListener(v -> exportHeatmapData());
        buttonCreateMap.setOnClickListener(v -> createNewMap());
        buttonViewMap.setOnClickListener(v -> viewActiveMap());


    }

    // Method for Admin to save a reference point
    private void saveReferencePoint() {
        Object selectedSsidItem = spinnerReferenceApSelection.getSelectedItem();
        if (selectedSsidItem == null) {
            Toast.makeText(this, "Please perform a scan and select an AP.", Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedDisplayText = selectedSsidItem.toString();
        String bssid = bssidToSsidMap.get(selectedDisplayText);
        if(bssid == null){
            Toast.makeText(this, "Could not find BSSID for selected network.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Getting SSID for display purposes
        String ssid;
        int parenthesisIndex = selectedDisplayText.indexOf(" (");
        if(parenthesisIndex != -1){
            ssid = selectedDisplayText.substring(0, parenthesisIndex);
        }
        else{
            ssid = selectedDisplayText;
        }
        String xStr = editRefX.getText().toString();
        String yStr = editRefY.getText().toString();
        String rssi1mStr = editRefRssi1m.getText().toString();

        if (xStr.isEmpty() || yStr.isEmpty() || rssi1mStr.isEmpty()) {
            Toast.makeText(this, "Please fill all reference point fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);
            double rssi1m = Double.parseDouble(rssi1mStr);

            if(referencePoints.isEmpty()){
                if(x != 0 || y != 0){
                    Toast.makeText(this,"First anchor point is the origin. Its coordinates must be (0, 0).", Toast.LENGTH_SHORT).show();
                    x = 0;
                    y = 0;
                    editRefX.setText("0");
                    editRefY.setText("0");
                }
            }
            ReferencePoint newRefPoint = new ReferencePoint(bssid, ssid, x, y, rssi1m);

            FingerprintRoomDatabase.databaseWriteExecutor.execute(() -> {
                fingerprintDao.insertReferencePoint(newRefPoint);
            });

            Toast.makeText(this, "Saved reference point for " + ssid, Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format.", Toast.LENGTH_SHORT).show();
        }
    }

    // Calculates the users position using trilateration
    private Position calculatePositionWithTrilateration() {
        if (referencePoints.size() < 3){
            // There is not enough reference points to trilaterate
            return null;
        }

        List<PointWithDistance> measurements = new ArrayList<>();
        for (ReferencePoint refPoint : referencePoints) {
            double rssi = getAverageRssiForBssid(refPoint.bssid);
            if (rssi != 0) {
                double distance = calculateDistance(rssi, refPoint.measuredPowerAtOneMeter, 3.0);
                measurements.add(new PointWithDistance(refPoint, distance));
            }
        }

        if (measurements.size() < 3) return null;

        // For simplicity, use the first 3 found measurements.
        // A better app would choose the 3 with the strongest signals.
        PointWithDistance m1 = measurements.get(0);
        PointWithDistance m2 = measurements.get(1);
        PointWithDistance m3 = measurements.get(2);

        return trilaterate(m1.point, m2.point, m3.point, m1.distance, m2.distance, m3.distance);
    }

    // Helper methods for trilateration
    private double getAverageRssiForBssid(String bssid) {
        WifiNetwork network = networkMap.get(bssid);
        return (network != null) ? network.getAverageSignalStrength() : 0;
    }

    private double calculateDistance(double rssi, double measuredPowerAt1m, double pathLossExponent) {
        double exponent = (measuredPowerAt1m - rssi) / (10 * pathLossExponent);
        return Math.pow(10, exponent);
    }

    private Position trilaterate(ReferencePoint p1, ReferencePoint p2, ReferencePoint p3, double d1, double d2, double d3) {
        double A = 2 * p2.x - 2 * p1.x;
        double B = 2 * p2.y - 2 * p1.y;
        double C = Math.pow(d1, 2) - Math.pow(d2, 2) - Math.pow(p1.x, 2) + Math.pow(p2.x, 2) - Math.pow(p1.y, 2) + Math.pow(p2.y, 2);
        double D = 2 * p3.x - 2 * p2.x;
        double E = 2 * p3.y - 2 * p2.y;
        double F = Math.pow(d2, 2) - Math.pow(d3, 2) - Math.pow(p2.x, 2) + Math.pow(p3.x, 2) - Math.pow(p2.y, 2) + Math.pow(p3.y, 2);

        double denominator = (E * A - B * D);
        if (Math.abs(denominator) < 1e-6) return null; // Avoid division by zero

        double x = (C * E - F * B) / denominator;
        double y = (C * D - A * F) / (B * D - A * E);

        return new Position(x, y);
    }

    public static class Position {
        double x, y;
        public Position(double x, double y) { this.x = x; this.y = y; }
    }

    private static class PointWithDistance {
        ReferencePoint point;
        double distance;
        PointWithDistance(ReferencePoint point, double distance){
            this.point = point; this.distance = distance;
        }
    }

    private void askForLocationPermission(){
        // Check if we already have the permission
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            // Permission is not granted so we have to ask for it
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION_REQUEST);

        }else {
            // If the permission is already granted, we can proceed
            Toast.makeText(this,"Permission already granted, starting the scan!",Toast.LENGTH_SHORT).show();
            startWifiScan();
        }
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Checking which permission request we have
        if(requestCode == LOCATION_PERMISSION_REQUEST){
            // Checking the fine location permission at grantResults[0]
            if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                // Permission granted
                Toast.makeText(this, "Permission granted!", Toast.LENGTH_SHORT).show();
                startWifiScan();

            }else {
                // Permission denied
                Toast.makeText(this, "Permission denied!", Toast.LENGTH_SHORT).show();

            }
        }
    }



    // Wifi scan & logic behind it
    private void startWifiScan(){
        // Checking the location services first
        if(!isLocationServicesEnabled()){
            // Location services are off, can't scan
            Toast.makeText(this, "Please enable Location Services to find Wi-Fi networks.",Toast.LENGTH_SHORT).show();
            // Open the location settings
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);

            // Stop the process, user must enable location services
            return;
        }


        // Check if WiFi is enabled
        if(!wifiManager.isWifiEnabled()){
            // it is not enabled so we should enable it
            Toast.makeText(this, "Please enable WiFi to scan for networks.", Toast.LENGTH_SHORT).show();

            // Launching the settings panel
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            settingsPanelLauncher.launch(panelIntent);


        }else {
            isScanningForMeasurements = true;
            scanCount = 0;
            wifiList.clear();
            networkMap.clear();
            wifiAdapter.notifyDataSetChanged();

            try{
                unregisterReceiver(wifiScanReceiver);
            } catch (IllegalArgumentException e){

            }

            registerReceiver(wifiScanReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

            buttonScan.setEnabled(false);
            buttonScan.setText("Scanning... (1/3)");
            // Wi-Fi is already enabled, we can proceed
            scanNowThatWifiIsEnabled();
        }

    }

    // Helper method for actual scan, manages the multiscan process
    private void scanNowThatWifiIsEnabled(){



        if(!wifiManager.startScan()){
            Toast.makeText(this, "Scan failed to start.",Toast.LENGTH_SHORT).show();
            resetScanState(); // Reset if scan fails
        }

    }



    private final BroadcastReceiver wifiScanReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent){

            if(!isScanningForMeasurements){
                return;
            }

            if(intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)){

                //Checking for permission just in case
                if(ActivityCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                    return;
                }

                scanCount++;
                // Getting scan results
                List<ScanResult> results = wifiManager.getScanResults();

                for(ScanResult result : results){
                    // Filtering out empty or null SSIDs
                    if(result.BSSID == null){
                        continue;
                    }
                    String bssid = result.BSSID;
                    String ssid = (result.SSID == null && result.SSID.isEmpty()) ? "<Hidden SSID>" :result.SSID;
                    WifiNetwork wifiNetwork = networkMap.get(bssid);
                    if(wifiNetwork == null){
                        // First time we see this network
                        wifiNetwork = new WifiNetwork (ssid, bssid);
                        networkMap.put(bssid, wifiNetwork);
                        wifiList.add(wifiNetwork);

                    }
                    // Adding the signal strength reading
                    if(!wifiNetwork.isMeasurementComplete){
                        wifiNetwork.signalStrengths.add(result.level);
                    }

                }

                // Notifying the adapter that the data has changed
                wifiAdapter.notifyDataSetChanged();

                if(scanCount < TOTAL_SCANS){
                    // We need more scans
                    buttonScan.setText(String.format(Locale.getDefault(),"Scanning... (%d/%d)",scanCount + 1 , TOTAL_SCANS));
                    scanNowThatWifiIsEnabled(); // triggering the next scan

                }else{
                    // We are done with all scans
                    for(WifiNetwork network : wifiList){
                        network.isMeasurementComplete = true;
                    }
                    wifiAdapter.notifyDataSetChanged(); // Final update to show averages
                    Toast.makeText(context, "Scan complete!", Toast.LENGTH_SHORT).show();
                    updateNetworkSelectorSpinner();
                    if(currentUserRole.equals("ADMIN")){
                        findMyPositionByFingerprint();  // Finding the position
                    }
                    else{
                        // Normal users- trilateration
                        calculateAndShowUserOnMap();
                    }

                    calculatePositionWithTrilateration();

                    startCooldown(); // Starting the cooldown
                }
            }


        }
    };

    private void calculateAndShowUserOnMap() {
        // 1. Calculate live position using Trilateration
        Position userPosition = calculatePositionWithTrilateration();

        Object selectedMapItem = spinnerActiveMap.getSelectedItem();
        if (userPosition == null){
            Toast.makeText(this, "Could not determine location. Are 3 anchors defined and visible?", Toast.LENGTH_LONG).show();
            return;
        }

        if(selectedMapItem == null){
            Toast.makeText(this, "No active map selected", Toast.LENGTH_LONG).show();
            return;
        }
        MapSession selectedMap = (MapSession) selectedMapItem;

        if(referencePoints.isEmpty()){
            Toast.makeText(this, "No reference points defined for heatmap", Toast.LENGTH_LONG).show();
            return;
        }

        String bssidForHeatmap = referencePoints.get(0).bssid;

        // 2. Launch MapDetailActivity with the map's heatmap data AND the user's live position
        Intent intent = new Intent(this, MapDetailActivity.class);
        intent.putExtra(MapDetailActivity.EXTRA_MAP_ID, selectedMap.id);
        intent.putExtra(MapDetailActivity.EXTRA_MAP_NAME, selectedMap.mapName);
        //intent.putExtra(MapDetailActivity.EXTRA_TARGET_SSID, bssidForHeatmap); // Pass the reliable BSSID

        // Pass the user's calculated trilateration coordinates
        intent.putExtra(MapDetailActivity.EXTRA_USER_X, userPosition.x);
        intent.putExtra(MapDetailActivity.EXTRA_USER_Y, userPosition.y);
        startActivity(intent);
    }


    /**
     * Updates the spinner with the list of unique SSIDs.
     */
    private void updateNetworkSelectorSpinner(){
//        List<String> spinnerDisplayList = (List<String>) spinnerNetworkSelectionAdapter.getView(0, null, null).getTag();
//        if (spinnerDisplayList == null) {
//            spinnerDisplayList = new ArrayList<>();
//        }
        networkSpinnerData.clear();
        bssidToSsidMap.clear();

        for(WifiNetwork network : wifiList){
            String displayText = network.ssid + " (" + network.bssid + ")";
            networkSpinnerData.add(displayText);
            bssidToSsidMap.put(displayText, network.bssid); // Map display text to BSSID
        }
        spinnerNetworkSelectionAdapter.notifyDataSetChanged();
    }


    /**
     * A 2-minute cooldown timer after a burst scan is complete
     */
    private void startCooldown(){
        // Scan is complete
        isScanningForMeasurements = false;
        scanCount = 0;
        buttonScan.setText(R.string.on_cooldown_text);  // Updating the button text
        textCooldownTimer.setVisibility(View.VISIBLE); // Showing the timer

        // Starting the timer
        cooldownTimer = new CountDownTimer(120000, 1000) {
            @Override
            public void onFinish() {
                resetScanState();
                Toast.makeText(MainActivity.this, "Cooldown finished. Ready to scan.", Toast.LENGTH_SHORT).show();


            }

            @Override
            public void onTick(long millisUntilFinished) {
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                String timeLeft = String.format(Locale.getDefault(), "Next scan in: %d:%02d", minutes, seconds);
                textCooldownTimer.setText(timeLeft);

            }
        }.start();

    }

    // Resetting the UI and state variables after a scan process is complete or failed
    private void resetScanState(){
        isScanningForMeasurements = false;
        scanCount = 0;
        buttonScan.setEnabled(true);
        buttonScan.setText(R.string.scan_wifi_button_text);
        textCooldownTimer.setVisibility(View.GONE);

        // To prevent any memory leaks or weird behavior
        if( cooldownTimer != null){
            cooldownTimer.cancel();
        }
        try {
            unregisterReceiver(wifiScanReceiver);
        }catch (IllegalArgumentException e){

        }
    }

    // Method to check the status of the location services
    private boolean isLocationServicesEnabled(){
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(locationManager == null){
            return false; // Location manager not available
        }

        // Check either GPS provider or the Network provider is enabled
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

    }

    /**
     * Saves the last completed scan as a new fingerprint in our database.
     */
    private void saveCurrentFingerprint(){

        // Checking the role just in case
        if(!currentUserRole.equals("ADMIN")){
            Toast.makeText(this, "Only admins can add fingerprints.", Toast.LENGTH_SHORT).show();
            return;
        }


        String xCoordinate = editCoordinateX.getText().toString();
        String yCoordinate = editCoordinateY.getText().toString();


        if(xCoordinate.isEmpty() || yCoordinate.isEmpty()){
            Toast.makeText(this, "Please enter both X and Y coordinates.",Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if a scan has been completed
        if(wifiList.isEmpty() || !wifiList.get(0).isMeasurementComplete){
            Toast.makeText(this, "Please complete a scan before saving a location!", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double x = Double.parseDouble(xCoordinate);
            double y = Double.parseDouble(yCoordinate);

            Map <String, Double> currentRssiMap = new HashMap<>();
            for(WifiNetwork network : wifiList){
                currentRssiMap.put(network.bssid, network.getAverageSignalStrength());
            }

            Fingerprint newFingerprint = new Fingerprint(activeMap.id, x, y, currentRssiMap);
            // Running the database insert operation on a background thread.
            FingerprintRoomDatabase.databaseWriteExecutor.execute(() -> {
                fingerprintDao.insert(newFingerprint);
            });


            Toast.makeText(this, "Saved fingerprint for: ("+ x + ", " + y + ")", Toast.LENGTH_SHORT).show();
            editCoordinateX.setText("");
            editCoordinateY.setText("");


        } catch (NumberFormatException e){
            Toast.makeText(this, "Invalid coordinate format.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Method returns the best match
     * @return Best matching Fingerprint object, or null if not found
     */
    private Fingerprint findMyPositionByFingerprint(){
        if(activeFingerprints.isEmpty()){
            textUserLocation.setText("Location map is empty. Add points first.");
            textUserLocation.setVisibility(View.VISIBLE);
            return null;
        }

        // Get the fingerprint from the latest scan;
        Map<String, Double> currentRssiMap = new HashMap<>();
        for(WifiNetwork network : wifiList){
            if(network.isMeasurementComplete){  // Only consider completed scans
                currentRssiMap.put(network.bssid, network.getAverageSignalStrength());

            }
        }

        Fingerprint bestMatch = null;
        double smallestDistance = Double.MAX_VALUE;

        //Comparing the current fingerprint to each one in our database
        for(Fingerprint savedFingerprint : activeFingerprints){
            double currentDistance = calculateFingerprintDistance(currentRssiMap, savedFingerprint.rssiMap);
            if(currentDistance < smallestDistance){
                smallestDistance = currentDistance;
                bestMatch = savedFingerprint;
            }
        }

        if(bestMatch != null){
            String locationText = String.format(Locale.getDefault(), "You are near: (%.2f, %.2f)", bestMatch.x, bestMatch.y);
            textUserLocation.setText(locationText);
            textUserLocation.setVisibility(View.VISIBLE);

        }
        return bestMatch;
    }

    /**
     * Calculates the "Euclidean distance" between two signal strength fingerprints.
     * @param current The currently measured RSSI map.
     * @param saved The saved RSSI map from the database.
     * @return A numeric value representing the difference between the two fingerprints.
     */
    private double calculateFingerprintDistance(Map<String, Double> current, Map<String, Double> saved){
        double sumOfSquares = 0.0;
        // Use the saved fingerprints APs as the basis for comparison
        for(String bssid : saved.keySet()){
            double currentStrength = current.getOrDefault(bssid, -100.0); // -100 as a penalty if AP is not found
            double savedRssi = saved.get(bssid);
            sumOfSquares += Math.pow(currentStrength - savedRssi, 2);
        }
        return Math.sqrt(sumOfSquares);
    }

    private void exportHeatmapData(){
        if(activeMap == null || activeFingerprints.isEmpty()){
            Toast.makeText(this, "No data to export", Toast.LENGTH_SHORT).show();
            return;
        }

        Object selectedItem = spinnerNetworkSelection.getSelectedItem();
        if(selectedItem == null){
            Toast.makeText(this, "No network selected to export.", Toast.LENGTH_SHORT).show();
            return;
        }

        String targetSsid = selectedItem.toString();
        String selectedDisplayText = selectedItem.toString();
        String targetBssid = bssidToSsidMap.get(selectedDisplayText);

        if(targetBssid == null ){
            return;
        }


        StringBuilder sb = new StringBuilder();
        sb.append("X,Y,RSSI\n"); // CSV Header


        // Looping through each saved fingerprint point to append the data
        for(Fingerprint point : activeFingerprints){
            // Getting the rssi data for our specific target AP, default to -100 if not found
            Double rssi = point.rssiMap.getOrDefault(targetBssid, -100.0);
            sb.append(String.format(Locale.getDefault(), "%.2f,%.2f,%.2f\n", point.x, point.y, rssi));
        }

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi Heatmap Data for " + targetBssid);
        shareIntent.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(shareIntent, "Export Heatmap Data"));
    }

    /**
     * Observes the fingerprints for the current map
     */
    private void observeFingerprintsForMap(){
        if(activeMap != null){
            fingerprintDao.getFingerprintsForMap(activeMap.id).removeObservers(this);
            // Adding new observer
            fingerprintDao.getFingerprintsForMap(activeMap.id).observe(this, fingerprints -> {
                activeFingerprints.clear();
                activeFingerprints.addAll(fingerprints);
            });
        }
    }

    private void createNewMap(){
        String mapName = editNewMapName.getText().toString();
        if(mapName.isEmpty()){
            Toast.makeText(this, "Please enter a map name.", Toast.LENGTH_SHORT).show();
            return;
        }

        MapSession newMap = new MapSession(mapName);
        // Run on the background thread
        FingerprintRoomDatabase.databaseWriteExecutor.execute(() -> {
            long newId =fingerprintDao.insertMap(newMap);
        });
    }

    /**
     * Launches the MapActivityDetail to show the data for the selected map
     */
    private void viewActiveMap(){
        Object selectedMapItem = spinnerActiveMap.getSelectedItem();
        if(activeMap == null){
            Toast.makeText(this, "Please select a map to view", Toast.LENGTH_SHORT).show();
            return;
        }
        MapSession selectedMap = (MapSession) selectedMapItem;

        if (activeFingerprints.isEmpty()){
            Toast.makeText(this, "No heatmap data has been saved for this map yet. Please use 'Save Point' first.", Toast.LENGTH_LONG).show();
            return;
        }


        Intent intent = new Intent(this, MapDetailActivity.class);
        // Pass the necessary data to the new activity
        intent.putExtra(MapDetailActivity.EXTRA_MAP_ID, selectedMap.id);
        intent.putExtra(MapDetailActivity.EXTRA_MAP_NAME, selectedMap.mapName);

        intent.putExtra(MapDetailActivity.EXTRA_USER_X, -1.0);
        intent.putExtra(MapDetailActivity.EXTRA_USER_Y, -1.0);

        startActivity(intent);
    }

}
