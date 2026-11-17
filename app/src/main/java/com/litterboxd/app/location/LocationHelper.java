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
import android.os.SystemClock;

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

    /** Overall limit for finding the phone. */
    private static final long TIMEOUT_MS = 30 * 1000L;
    /** A fix at least this accurate (GPS outdoors, good Wi-Fi) is used straight away. */
    static final float GOOD_ACCURACY_M = 50f;
    /** After a rougher fix, wait this long for a better one (usually GPS) before using the best. */
    private static final long SETTLE_MS = 8 * 1000L;
    /** Clock slack when checking that a fix was taken after the request started. */
    private static final long CLOCK_SLACK_MS = 1000L;

    private final LocationManager locationManager;
    private LocationListener activeListener;
    private final List<LocationListener> singleListeners = new ArrayList<>();
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable;
    private Runnable settleRunnable;
    private Location bestSoFar;

    public LocationHelper(Context context) {
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Finds where the phone is right now. Saved positions are never used: every enabled provider
     * (GPS, Network and, from Android 12, the system's "fused" one) is asked for a new fix, and only
     * fixes taken after this call count, because some phones hand back an old saved position (or
     * 0,0) the moment updates are requested.
     * A fix within 50 m is used at once. A rougher one (Wi-Fi or cell towers) is kept while the
     * others get 8 more seconds to do better, since GPS is slower but more exact; then the most
     * accurate fix is used. With no fix at all after 30 seconds, it reports a time-out.
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
        if (providers.isEmpty()) {
            callback.onError(ERROR_LOCATION_OFF, "No location providers are enabled.");
            return;
        }

        final long startNanos = SystemClock.elapsedRealtimeNanos();
        final long startWallMs = System.currentTimeMillis();
        try {
            for (String provider : providers) {
                LocationListener listener = new LocationListener() {
                    @Override
                    public void onLocationChanged(@NonNull Location location) {
                        onFix(location, startNanos, startWallMs, callback);
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
            Location best = bestSoFar;
            stop();
            if (best != null) {
                callback.onLocationResult(best);
            } else {
                callback.onError(ERROR_TIMEOUT, "Location request timed out.");
            }
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);
    }

    private void onFix(Location location, long startNanos, long startWallMs, LocationCallback callback) {
        if (location == null || !isPlausible(location.getLatitude(), location.getLongitude())) return;
        if (!takenAfter(location.getElapsedRealtimeNanos(), location.getTime(), startNanos, startWallMs)) {
            return; // an old position handed back again: keep waiting for a new one
        }
        if (location.hasAccuracy() && location.getAccuracy() <= GOOD_ACCURACY_M) {
            stop();
            callback.onLocationResult(location);
            return;
        }
        if (bestSoFar == null || accuracyOf(location) < accuracyOf(bestSoFar)) {
            bestSoFar = location;
        }
        if (settleRunnable == null) {
            settleRunnable = () -> {
                Location best = bestSoFar;
                stop();
                callback.onLocationResult(best);
            };
            timeoutHandler.postDelayed(settleRunnable, SETTLE_MS);
        }
    }

    private static float accuracyOf(Location location) {
        return location.hasAccuracy() ? location.getAccuracy() : Float.MAX_VALUE;
    }

    /**
     * True if a fix was taken after the request started. Uses the phone's uptime clock when the
     * fix has it (a wrong date setting can't upset it), otherwise the wall clock.
     */
    static boolean takenAfter(long fixNanos, long fixWallMs, long startNanos, long startWallMs) {
        if (fixNanos > 0) return fixNanos >= startNanos - CLOCK_SLACK_MS * 1_000_000L;
        return fixWallMs >= startWallMs - CLOCK_SLACK_MS;
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

    /** False for NaN, out-of-range values and 0,0, which some phones report when they have no fix. */
    static boolean isPlausible(double latitude, double longitude) {
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) return false;
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) return false;
        return Math.abs(latitude) > 0.001 || Math.abs(longitude) > 0.001;
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
        if (settleRunnable != null) {
            timeoutHandler.removeCallbacks(settleRunnable);
            settleRunnable = null;
        }
        bestSoFar = null;
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
