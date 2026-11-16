package com.litterboxd.app.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import java.util.ArrayList;
import java.util.List;

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

    /** Why no location came back, so the screen can say what to do about it. */
    public static final int ERROR_PERMISSION = 1;
    /** Location is switched off for the whole phone. */
    public static final int ERROR_LOCATION_OFF = 2;
    /** Location is on, but no provider found the phone in time (often indoors). */
    public static final int ERROR_TIMEOUT = 3;
    public static final int ERROR_OTHER = 4;

    public interface LocationCallback {
        void onLocationResult(Location location);
        void onError(int reason, String errorMessage);
    }

    private static final long FRESH_MS = 5 * 60 * 1000L;
    /** After a timeout, a fix up to an hour old is still good enough to find vets within 10 km. */
    private static final long FALLBACK_MS = 60 * 60 * 1000L;
    private static final long TIMEOUT_MS = 30 * 1000L;

    private final LocationManager locationManager;
    private LocationListener activeListener;
    private final List<LocationListener> singleListeners = new ArrayList<>();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;

    public LocationHelper(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Attempts to get a single location fix. Uses the newest last known location from any provider
     * if it is under 5 minutes old. Otherwise asks every enabled provider at once (GPS, Network and,
     * from Android 12, the system's own "fused" one) and takes the first answer, because GPS alone
     * often finds nothing indoors while Wi-Fi based Network location does.
     * After 30 seconds with no answer it falls back to a last known location up to an hour old.
     */
    public void getSingleLocation(Context context, @NonNull LocationCallback callback) {
        stop();
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback.onError(ERROR_PERMISSION, "Location permission not granted.");
            return;
        }

        if (locationManager == null) {
            callback.onError(ERROR_OTHER, "LocationManager not available.");
            return;
        }

        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            callback.onError(ERROR_LOCATION_OFF, "Location is turned off on this phone.");
            return;
        }

        // Before Android 12, GPS needs "precise" permission. From Android 12 the system lets an app
        // with only "approximate" permission use GPS and blurs the result itself, which matters on
        // phones in China whose Network provider is missing or off without Google services.
        final boolean hasFine = ContextCompat.checkSelfPermission(context,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;

        final List<String> providers = enabledProviders(hasFine);
        final Location fresh = newestLastKnown(providers, FRESH_MS);
        if (fresh != null) {
            callback.onLocationResult(fresh);
            return;
        }

        if (providers.isEmpty()) {
            callback.onError(ERROR_LOCATION_OFF, "No location providers are enabled.");
            return;
        }

        try {
            for (String provider : providers) {
                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        stop();
                        callback.onLocationResult(location);
                    }

                    @Override
                    public void onStatusChanged(String p, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(@NonNull String p) {}

                    @Override
                    public void onProviderDisabled(@NonNull String p) {}
                };
                singleListeners.add(listener);
                locationManager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
            }
        } catch (SecurityException | IllegalArgumentException e) {
            stop();
            callback.onError(ERROR_OTHER, "Couldn't request location: " + e.getMessage());
            return;
        }

        timeoutRunnable = () -> {
            stop();
            Location older = newestLastKnown(providers, FALLBACK_MS);
            if (older != null) {
                callback.onLocationResult(older);
            } else {
                callback.onError(ERROR_TIMEOUT, "Location request timed out.");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private List<String> enabledProviders(boolean hasFine) {
        List<String> providers = new ArrayList<>();
        if (hasFine && isEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (isEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isEnabled(LocationManager.FUSED_PROVIDER)) {
            providers.add(LocationManager.FUSED_PROVIDER);
        }
        return providers;
    }

    private boolean isEnabled(String provider) {
        try {
            return locationManager.isProviderEnabled(provider);
        } catch (IllegalArgumentException e) {
            return false; // the phone doesn't have this provider at all
        }
    }

    /** Newest last known fix no older than maxAgeMs; the passive provider adds fixes other apps got. */
    private Location newestLastKnown(List<String> providers, long maxAgeMs) {
        List<String> all = new ArrayList<>(providers);
        all.add(LocationManager.PASSIVE_PROVIDER);
        long oldestAllowed = System.currentTimeMillis() - maxAgeMs;
        Location best = null;
        for (String provider : all) {
            try {
                Location last = locationManager.getLastKnownLocation(provider);
                if (last != null && last.getTime() > oldestAllowed
                        && (best == null || last.getTime() > best.getTime())) {
                    best = last;
                }
            } catch (SecurityException | IllegalArgumentException ignored) {
                // no permission for, or no such, provider: try the next one
            }
        }
        return best;
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
            callback.onError(ERROR_PERMISSION, "Location permission not granted.");
            return;
        }

        if (locationManager == null) {
            callback.onError(ERROR_OTHER, "LocationManager not available.");
            return;
        }

        if (!locationManager.isProviderEnabled(provider)) {
            callback.onError(ERROR_LOCATION_OFF, "Location provider " + provider + " is disabled.");
            return;
        }

        activeListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                callback.onLocationResult(location);
            }

            @Override
            public void onStatusChanged(String p, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(@NonNull String p) {}

            @Override
            public void onProviderDisabled(@NonNull String p) {}
        };

        try {
            locationManager.requestLocationUpdates(provider, minTimeMs, minDistanceM, activeListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onError(ERROR_OTHER, "Security exception: " + e.getMessage());
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
        if (locationManager == null) return;
        if (activeListener != null) {
            try {
                locationManager.removeUpdates(activeListener);
            } catch (SecurityException ignored) {}
            activeListener = null;
        }
        for (LocationListener listener : singleListeners) {
            try {
                locationManager.removeUpdates(listener);
            } catch (SecurityException ignored) {}
        }
        singleListeners.clear();
    }
}
