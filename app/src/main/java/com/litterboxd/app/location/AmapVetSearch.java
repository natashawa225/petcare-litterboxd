package com.litterboxd.app.location;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Finds vet clinics near a phone position with the AMap (Gaode) Web Service API. AMap is used
 * because Google services are blocked in mainland China; it is a plain HTTPS API, so no SDK.
 *
 * The phone's GPS gives WGS-84 coordinates but AMap works in GCJ-02, so the position is
 * converted first. The search uses AMap's POI category 090700 "动物医疗场所" (vet hospitals)
 * and 090701 "宠物诊所" (pet clinics); 090702 "兽医站" is left out because those are livestock
 * stations. A category is needed for AMap to sort by distance.
 * Plain Java (apart from org.json) so the request and parsing can be unit-tested.
 */
public final class AmapVetSearch {

    static final String ORIGIN = "https://restapi.amap.com";
    static final String VET_TYPES = "090700|090701";
    static final int RADIUS_METERS = 10000;
    static final int PAGE_SIZE = 10;
    public static final int MAX_RESULTS = 3;
    private static final int TIMEOUT_MS = 8000;
    private static final int MAX_BYTES = 200_000;

    /** AMap answered, but with status 0 (for example a wrong key or a used-up daily quota). */
    public static final class ServiceException extends Exception {
        public ServiceException(String info) {
            super(info);
        }
    }

    private final String key;

    public AmapVetSearch(String key) {
        this.key = key == null ? "" : key.trim();
    }

    public boolean hasKey() {
        return !key.isEmpty();
    }

    /** Background thread. Up to {@link #MAX_RESULTS} clinics, closest first. */
    public List<VetClinic> findNearby(double wgsLatitude, double wgsLongitude)
            throws IOException, ServiceException {
        double[] gcj = parseConvert(get(convertUrl(key, wgsLongitude, wgsLatitude)));
        List<VetClinic> clinics = parseAround(get(aroundUrl(key, gcj[0], gcj[1])));
        return clinics.size() > MAX_RESULTS ? new ArrayList<>(clinics.subList(0, MAX_RESULTS)) : clinics;
    }

    static String convertUrl(String key, double longitude, double latitude) throws IOException {
        return ORIGIN + "/v3/assistant/coordinate/convert?coordsys=gps&output=json"
                + "&locations=" + lngLat(longitude, latitude)
                + "&key=" + encode(key);
    }

    static String aroundUrl(String key, double longitude, double latitude) throws IOException {
        return ORIGIN + "/v5/place/around?sortrule=distance&show_fields=business"
                + "&types=" + encode(VET_TYPES)
                + "&radius=" + RADIUS_METERS
                + "&page_size=" + PAGE_SIZE
                + "&location=" + lngLat(longitude, latitude)
                + "&key=" + encode(key);
    }

    /** AMap wants "longitude,latitude" with at most 6 decimals. */
    static String lngLat(double longitude, double latitude) {
        return String.format(Locale.US, "%.6f,%.6f", longitude, latitude);
    }

    /** {longitude, latitude} in GCJ-02. */
    static double[] parseConvert(String json) throws ServiceException {
        try {
            JSONObject root = checkStatus(json);
            String first = root.getString("locations").split(";")[0];
            String[] parts = first.split(",");
            return new double[]{Double.parseDouble(parts[0]), Double.parseDouble(parts[1])};
        } catch (JSONException | RuntimeException e) {
            throw new ServiceException("Unreadable coordinate reply");
        }
    }

    /** Every clinic in the reply, closest first. */
    static List<VetClinic> parseAround(String json) throws ServiceException {
        List<VetClinic> clinics = new ArrayList<>();
        try {
            JSONArray pois = checkStatus(json).optJSONArray("pois");
            if (pois == null) return clinics;
            for (int i = 0; i < pois.length(); i++) {
                JSONObject poi = pois.getJSONObject(i);
                String name = text(poi, "name");
                if (name.isEmpty()) continue;
                JSONObject business = poi.optJSONObject("business");
                clinics.add(new VetClinic(name, text(poi, "address"),
                        distance(text(poi, "distance")),
                        business == null ? "" : firstPhone(text(business, "tel"))));
            }
        } catch (JSONException e) {
            throw new ServiceException("Unreadable search reply");
        }
        clinics.sort(Comparator.comparingInt(VetClinic::getDistanceMeters));
        return clinics;
    }

    private static JSONObject checkStatus(String json) throws JSONException, ServiceException {
        JSONObject root = new JSONObject(json);
        if (!"1".equals(root.optString("status"))) {
            throw new ServiceException(root.optString("info", "Unknown error"));
        }
        return root;
    }

    /** AMap sends an empty value as [] instead of "", so arrays count as empty. */
    private static String text(JSONObject o, String name) {
        Object value = o.opt(name);
        return value instanceof String ? ((String) value).trim() : "";
    }

    private static int distance(String meters) {
        try {
            return (int) Math.round(Double.parseDouble(meters));
        } catch (NumberFormatException e) {
            return VetClinic.UNKNOWN_DISTANCE;
        }
    }

    /** Several numbers come back separated by ";", so only the first is kept. */
    static String firstPhone(String tel) {
        return tel == null ? "" : tel.split(";")[0].trim();
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String get(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("HTTP " + connection.getResponseCode());
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int total = 0;
                int read;
                while ((read = in.read(buffer)) != -1 && total < MAX_BYTES) {
                    out.write(buffer, 0, read);
                    total += read;
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            connection.disconnect();
        }
    }
}
