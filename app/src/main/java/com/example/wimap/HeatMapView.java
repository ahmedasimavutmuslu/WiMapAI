package com.example.wimap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HeatMapView extends AppCompatImageView {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // Optimization: Calculate heatmap at a fraction of the resolution, then scale up.
    // 0.1f means we calculate 10% of the width/height (1/100th total pixels).
    // Bilinear filtering during upscale handles the smoothing.
    private static final float DOWNSAMPLE_SCALE = 0.125f;

    public HeatMapView(@NonNull Context context){
        super(context);
    }

    public HeatMapView(@NonNull Context context, @Nullable AttributeSet attributeSet){
        super(context, attributeSet);
    }

    public void generateHeatmap(List<Fingerprint> points, List<String> anchorBssids, int viewWidth, int viewHeight, double userX, double userY){
        if(points == null || points.isEmpty() || anchorBssids == null || anchorBssids.isEmpty() || viewWidth <= 0 || viewHeight <= 0){
            return;
        }

        executor.execute(() -> {
            long startTime = System.currentTimeMillis();

            // 1. Calculate the smaller grid dimensions for performance
            int calcWidth = (int) (viewWidth * DOWNSAMPLE_SCALE);
            int calcHeight = (int) (viewHeight * DOWNSAMPLE_SCALE);

            // Ensure at least 1 pixel
            if (calcWidth < 1) calcWidth = 1;
            if (calcHeight < 1) calcHeight = 1;

            // 2. Do the heavy math on the small grid
            Bitmap smallHeatmap = createLowResHeatmap(points, anchorBssids, calcWidth, calcHeight);

            // 3. Scale it up to the full view size (Bilinear filtering makes it smooth)
            Bitmap finalBitmap = Bitmap.createScaledBitmap(smallHeatmap, viewWidth, viewHeight, true);

            // 4. Draw User marker on top of the high-res bitmap
            drawUserMarker(finalBitmap, points, viewWidth, viewHeight, userX, userY);

            long duration = System.currentTimeMillis() - startTime;
            Log.d("HeatmapPerf", "Generated in " + duration + "ms");

            mainThreadHandler.post(() -> {
                this.setImageBitmap(finalBitmap);
                // Optional: Don't toast on every update if this runs frequently
                // Toast.makeText(getContext(), "Heatmap Updated", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private Bitmap createLowResHeatmap(List<Fingerprint> points, List<String> anchorBssids, int width, int height) {
        // finding boundaries
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;





        for(Fingerprint point : points){
            if(point.x < minX) minX = point.x;
            if(point.x > maxX) maxX = point.x;
            if(point.y < minY) minY = point.y;
            if(point.y > maxY) maxY = point.y;
        }

        // Add padding to prevent "line" maps or edge clipping
        double paddingX = (maxX - minX) * 0.1; // 10% padding
        double paddingY = (maxY - minY) * 0.1;

        // Handle case where all points are identical or in a line
        if (paddingX == 0) paddingX = 5.0;
        if (paddingY == 0) paddingY = 5.0;

        minX -= paddingX;
        maxX += paddingX;
        minY -= paddingY;
        maxY += paddingY;

        double SCALE = 50.0;

        // Move (0,0) to the center of the bitmap
        double CENTER_X = width / 2.0;
        double CENTER_Y = height / 2.0;

        // --- Optimization: Array Batching ---
        // Instead of bitmap.setPixel (slow), we write to an int array.
        int[] pixels = new int[width * height];

        for(int py = 0; py < height; py++){
            for(int px = 0; px < width; px++){

                // Map pixel coordinate to real-world coordinate
                double mapX = minX + (px / (double)width) * (maxX - minX);
                // Inverted Y for drawing
                double mapY = maxY - (py / (double)height) * (maxY - minY);

                double bestRssiAtPoint = -100.0;

                // Optimization: Inlining logic to avoid method call overhead in tight loops
                for(String anchorBssid : anchorBssids){
                    double interpolatedRssi = getInterpolatedRssi(mapX, mapY, points, anchorBssid);
                    if(interpolatedRssi > bestRssiAtPoint){
                        bestRssiAtPoint = interpolatedRssi;
                    }
                }

                // Store color in array
                pixels[py * width + px] = getColorForRssi(bestRssiAtPoint);
            }
        }

        // Create the bitmap from the array
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void drawUserMarker(Bitmap bitmap, List<Fingerprint> points, int width, int height, double userX, double userY) {
        // 1. Safety Check: If user position is invalid, don't draw anything
        if (userX == -1 || userY == -1) return;

        // 2. Re-calculate the exact same boundaries used in the Heatmap
        // (This ensures the dot aligns perfectly with the map)
        double minX = Double.MAX_VALUE; double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE; double maxY = -Double.MAX_VALUE;

        for(Fingerprint point : points){
            if(point.x < minX) minX = point.x;
            if(point.x > maxX) maxX = point.x;
            if(point.y < minY) minY = point.y;
            if(point.y > maxY) maxY = point.y;
        }

        // Apply the same 10% padding
        double paddingX = (maxX - minX) * 0.1;
        double paddingY = (maxY - minY) * 0.1;

        // Prevent division by zero if all points are in one spot
        if (paddingX == 0) paddingX = 5.0;
        if (paddingY == 0) paddingY = 5.0;

        minX -= paddingX;
        maxX += paddingX;
        minY -= paddingY;
        maxY += paddingY;

        double mapWidth = maxX - minX;
        double mapHeight = maxY - minY;

        // 3. Convert User's Real-World (x,y) to Screen Pixels (px,py)
        if (mapWidth > 0 && mapHeight > 0) {

            // X: Standard linear mapping
            int userPx = (int) ((userX - minX) / mapWidth * width);

            // Y: Inverted mapping (because screen Y grows downwards, but math Y grows upwards)
            // This matches your heatmap generation logic: "maxY - ..."
            int userPy = (int) ((maxY - userY) / mapHeight * height);

            Canvas canvas = new Canvas(bitmap);

            // --- STYLE 1: The White Inner Dot ---
            Paint markerPaint = new Paint();
            markerPaint.setColor(Color.WHITE); // White color
            markerPaint.setStyle(Paint.Style.FILL); // Solid fill
            markerPaint.setAntiAlias(true); // Smooth edges

            // --- STYLE 2: The Black Outline (To make it visible on light backgrounds) ---
            Paint outlinePaint = new Paint();
            outlinePaint.setColor(Color.BLACK);
            outlinePaint.setStyle(Paint.Style.STROKE); // Outline only
            outlinePaint.setStrokeWidth(5f); // Thicker border
            outlinePaint.setAntiAlias(true);

            // 4. Draw the Dot
            float radius = 25f; // Size of the dot (adjust as needed)

            // Draw White Circle
            canvas.drawCircle(userPx, userPy, radius, markerPaint);

            // Draw Black Border
            canvas.drawCircle(userPx, userPy, radius, outlinePaint);
        }
    }

    // Kept your IDW logic, added simple micro-optimization
    private double getInterpolatedRssi(double x, double y, List<Fingerprint> knownPoints, String targetBssid){
        double totalWeight = 0;
        double weightedRssiSum = 0;

        // We need to track how close the NEAREST data point is.
        double closestDistanceSquared = Double.MAX_VALUE;

        for(Fingerprint point : knownPoints){
            if(point.rssiMap == null || !point.rssiMap.containsKey(targetBssid)){
                continue;
            }

            double dx = x - point.x;
            double dy = y - point.y;
            double distanceSquared = dx*dx + dy*dy;

            // Track the closest point
            if(distanceSquared < closestDistanceSquared){
                closestDistanceSquared = distanceSquared;
            }

            // Exact match optimization
            if(distanceSquared < 0.000001){
                return point.rssiMap.get(targetBssid);
            }

            double weight = 1.0 / distanceSquared;
            double rssi = point.rssiMap.get(targetBssid);

            if(Double.isFinite(rssi) && Double.isFinite(weight)){
                weightedRssiSum += weight * rssi;
                totalWeight += weight;
            }
        }

        if(totalWeight == 0) return -100.0;

        double interpolatedValue = weightedRssiSum / totalWeight;

        // --- FIX: Signal Decay ---
        // If the pixel is too far from the nearest point, the signal should weaken.
        // You need to define a "Radius of Influence" (how far a point's signal reaches).
        // This value depends on your coordinate system (Meters vs Pixels).
        // Let's assume your coordinates are meters: 10.0 = 10 meters.
        double radiusOfInfluence = 10.0;
        double radiusSquared = radiusOfInfluence * radiusOfInfluence;

        if (closestDistanceSquared > radiusSquared) {
            // If we are outside the radius, we linearly fade the signal to -100 (Blue)
            // This prevents the "Solid Green Background" issue.

            // Calculate how far out we are (1.0 = at the edge, 2.0 = double the distance)
            double ratio = Math.sqrt(closestDistanceSquared) / radiusOfInfluence;

            // Penalize the signal based on distance
            // The further away, the more we subtract from the RSSI
            interpolatedValue -= (ratio - 1.0) * 10.0;

            // Clamp to minimum signal
            if (interpolatedValue < -100.0) interpolatedValue = -100.0;
        }

        return interpolatedValue;
    }

    private int getColorForRssi(double rssi){
        // Defining RSSI range. -30 (Strong) to -90 (Weak)
        double minRssi = -90;
        double maxRssi = -30;

        if (rssi < minRssi) rssi = minRssi;
        if (rssi > maxRssi) rssi = maxRssi;

        double normalizedValue = (rssi - minRssi) / (maxRssi - minRssi);

        // Blue (240) to Red (0) looks more "Heatmap-ish" than Blue to Green
        // But sticking to your hue logic:
        float hue = 240 - (float)(normalizedValue * 240);

        return Color.HSVToColor(new float[]{hue, 1.0f, 1.0f});
    }
}