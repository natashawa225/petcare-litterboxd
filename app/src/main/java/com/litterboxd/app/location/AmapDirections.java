package com.litterboxd.app.location;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Links that open directions to a clinic. The AMap app is tried first (it does the navigation);
 * if it isn't installed, AMap's route page opens in the browser instead. Both start from the
 * user's current position and take AMap (GCJ-02) coordinates as they are.
 * Formats from AMap's Android URI guide (route/plan) and URI API guide (navigation).
 */
public final class AmapDirections {

    public static final String AMAP_PACKAGE = "com.autonavi.minimap";
    static final String SOURCE = "Litterboxd";

    private AmapDirections() {}

    /** amapuri://route/plan: t=0 driving (changeable in AMap), dev=0 coordinates already GCJ-02. */
    public static String appUri(VetClinic clinic) {
        StringBuilder uri = new StringBuilder("amapuri://route/plan/?sourceApplication=")
                .append(encode(SOURCE));
        if (!clinic.getPoiId().isEmpty()) uri.append("&did=").append(encode(clinic.getPoiId()));
        return uri.append("&dlat=").append(coordinate(clinic.getLatitude()))
                .append("&dlon=").append(coordinate(clinic.getLongitude()))
                .append("&dname=").append(encode(clinic.getName()))
                .append("&dev=0&t=0")
                .toString();
    }

    /** https://uri.amap.com/navigation: "to" is lon,lat,name; no "from" means current position. */
    public static String webUri(VetClinic clinic) {
        // A comma in the name would be read as another part of "to".
        String name = clinic.getName().replace(',', ' ').replace('，', ' ');
        return "https://uri.amap.com/navigation?to=" + coordinate(clinic.getLongitude()) + ","
                + coordinate(clinic.getLatitude()) + "," + encode(name)
                + "&mode=car&src=" + encode(SOURCE) + "&callnative=0";
    }

    private static String coordinate(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
