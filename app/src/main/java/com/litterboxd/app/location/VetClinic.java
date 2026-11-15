package com.litterboxd.app.location;

import java.io.Serializable;

/** One vet clinic from an AMap nearby search. Serializable so results survive rotation. */
public final class VetClinic implements Serializable {

    private static final long serialVersionUID = 2L;

    /** Used when AMap leaves out the distance; sorts such clinics last. */
    public static final int UNKNOWN_DISTANCE = Integer.MAX_VALUE;

    private final String name;
    private final String address;
    private final int distanceMeters;
    private final String phone;
    /** AMap's own id for the place, "" if missing; lets AMap open the exact clinic. */
    private final String poiId;
    /** AMap (GCJ-02) position, NaN if missing. */
    private final double longitude;
    private final double latitude;

    public VetClinic(String name, String address, int distanceMeters, String phone) {
        this(name, address, distanceMeters, phone, "", Double.NaN, Double.NaN);
    }

    public VetClinic(String name, String address, int distanceMeters, String phone,
                     String poiId, double longitude, double latitude) {
        this.name = name == null ? "" : name;
        this.address = address == null ? "" : address;
        this.distanceMeters = distanceMeters;
        this.phone = phone == null ? "" : phone;
        this.poiId = poiId == null ? "" : poiId;
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getDistanceMeters() { return distanceMeters; }
    public String getPhone() { return phone; }
    public String getPoiId() { return poiId; }
    public double getLongitude() { return longitude; }
    public double getLatitude() { return latitude; }

    /** Directions need a position. */
    public boolean hasPosition() {
        return !Double.isNaN(longitude) && !Double.isNaN(latitude);
    }
}
