package com.litterboxd.app.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/**
 * Helper class encapsulating native LocationManager location retrieval and updates.
 * Reused and adapted from LocationServiceDemo.
 *
 * TODO for future Activity using LocationHelper:
 * Request runtime permissions before calling location methods:
 * if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
 *     ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
 * }
 * And handle onRequestPermissionsResult().
 */
public class LocationHelper {

    public interface LocationCallback {
        void onLocationResult(Location location);
        void onError(String errorMessage);
    }

    private final LocationManager locationManager;
    private LocationListener activeListener;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public LocationHelper(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Attempts to get a single location fix. Checks last known location on GPS then Network.
     * If null or older than 5 minutes (300,000 ms), requests one fresh update with a 30-second timeout.
     */
    public void getSingleLocation(Context context, @NonNull LocationCallback callback) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Location permission not granted.");
            return;
        }

        if (locationManager == null) {
            callback.onError("LocationManager not available.");
            return;
        }

        Location bestLocation = null;
        long fiveMinutesAgo = System.currentTimeMillis() - 300000L;

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                Location gpsLast = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (gpsLast != null && gpsLast.getTime() > fiveMinutesAgo) {
                    bestLocation = gpsLast;
                }
            }
            if (bestLocation == null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location netLast = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (netLast != null && netLast.getTime() > fiveMinutesAgo) {
                    bestLocation = netLast;
                }
            }
        } catch (SecurityException e) {
            callback.onError("Security exception accessing location: " + e.getMessage());
            return;
        }

        if (bestLocation != null) {
            callback.onLocationResult(bestLocation);
            return;
        }

        // Fallback: Request single fresh update
        final String provider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ?
                LocationManager.GPS_PROVIDER :
                (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null);

        if (provider == null) {
            callback.onError("No location providers (GPS or Network) are enabled.");
            return;
        }

        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                stop();
                callback.onLocationResult(location);
            }

            @Override
            public void onProviderEnabled(@NonNull String p) {}

            @Override
            public void onProviderDisabled(@NonNull String p) {}
        };

        try {
            locationManager.requestLocationUpdates(provider, 0, 0, activeListener, Looper.getMainLooper());

            // 30-second timeout
            timeoutRunnable = () -> {
                stop();
                callback.onError("Location request timed out.");
            };
            timeoutHandler.postDelayed(timeoutRunnable, 30000);

        } catch (SecurityException e) {
            callback.onError("Security exception requesting location: " + e.getMessage());
        }
    }

    /**
     * Starts continuous location updates with reasonable minTime (10000ms = 10s) and minDistance (10m).
     *
     * BATTERY VS ACCURACY TRADE-OFF:
     * Requesting updates with minTime = 0 and minDistance = 0 (as in basic demos) triggers continuous
     * high-frequency GPS fixes, draining battery very quickly. Using a threshold like 10 seconds and
     * 10 meters reduces CPU wakeups and power consumption while remaining responsive for navigation/vet finding.
     */
    public void startContinuousUpdates(Context context, String provider, long minTimeMs, float minDistanceM, @NonNull LocationCallback callback) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onError("Location permission not granted.");
            return;
        }

        if (locationManager == null) {
            callback.onError("LocationManager not available.");
            return;
        }

        if (!locationManager.isProviderEnabled(provider)) {
            callback.onError("Location provider " + provider + " is disabled.");
            return;
        }

        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                callback.onLocationResult(location);
            }

            @Override
            public void onProviderEnabled(@NonNull String p) {}

            @Override
            public void onProviderDisabled(@NonNull String p) {}
        };

        try {
            locationManager.requestLocationUpdates(provider, minTimeMs, minDistanceM, activeListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onError("Security exception: " + e.getMessage());
        }
    }

    /**
     * Stops listening for location updates and clears timeout handlers.
     * Callers must invoke this in onPause() or onDestroy().
     */
    public void stop() {
        if (timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (locationManager != null && activeListener != null) {
            try {
                locationManager.removeUpdates(activeListener);
            } catch (SecurityException ignored) {}
            activeListener = null;
        }
    }
}
