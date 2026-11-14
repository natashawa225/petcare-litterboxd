package com.litterboxd.app.location;

import java.io.Serializable;

/** One vet clinic from an AMap nearby search. Serializable so results survive rotation. */
public final class VetClinic implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Used when AMap leaves out the distance; sorts such clinics last. */
    public static final int UNKNOWN_DISTANCE = Integer.MAX_VALUE;

    private final String name;
    private final String address;
    private final int distanceMeters;
    private final String phone;

    public VetClinic(String name, String address, int distanceMeters, String phone) {
        this.name = name == null ? "" : name;
        this.address = address == null ? "" : address;
        this.distanceMeters = distanceMeters;
        this.phone = phone == null ? "" : phone;
    }

    public String getName() { return name; }
    public String getAddress() { return address; }
    public int getDistanceMeters() { return distanceMeters; }
    public String getPhone() { return phone; }
}
